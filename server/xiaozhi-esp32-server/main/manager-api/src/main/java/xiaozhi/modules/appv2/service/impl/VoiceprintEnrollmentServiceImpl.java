package xiaozhi.modules.appv2.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.AllArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.user.UserDetail;
import xiaozhi.modules.appv2.dao.V2AccountDao;
import xiaozhi.modules.appv2.dao.V2DeviceBindingDao;
import xiaozhi.modules.appv2.dao.V2MemberDao;
import xiaozhi.modules.appv2.dao.V2VoiceprintDao;
import xiaozhi.modules.appv2.dto.V2VoiceprintCacheEntry;
import xiaozhi.modules.appv2.dto.V2VoiceprintEnrollRequest;
import xiaozhi.modules.appv2.dto.V2VoiceprintEnrollResponse;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2MemberEntity;
import xiaozhi.modules.appv2.entity.V2VoiceprintEntity;
import xiaozhi.modules.appv2.service.VoiceprintEnrollmentService;
import xiaozhi.modules.security.user.SecurityUser;

@Service
@AllArgsConstructor
public class VoiceprintEnrollmentServiceImpl implements VoiceprintEnrollmentService {
    private static final String BINDING_STATUS_ACTIVE = "active";
    private static final String STATUS_ACTIVE = "active";
    private static final String ACCOUNT_STATUS_DELETED = "deleted";
    private static final String PROVIDER_LOCAL_FAKE = "local_fake_voiceprint";
    private static final int MIN_SAMPLE_DURATION_MS = 5_000;
    private static final int MAX_SAMPLE_DURATION_MS = 8_000;

    private final V2DeviceBindingDao v2DeviceBindingDao;
    private final V2MemberDao v2MemberDao;
    private final V2VoiceprintDao v2VoiceprintDao;
    private final V2AccountDao v2AccountDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2VoiceprintEnrollResponse enroll(V2VoiceprintEnrollRequest request) {
        UserDetail user = requireUser();
        String deviceId = required(request.getDeviceId());
        ensureActiveBinding(user.getId(), deviceId);
        int durationMs = requireEnrollmentDuration(request.getSampleDurationMs());
        byte[] audio = decodeAudio(request.getAudioBase64());
        String audioHash = sha256(audio);

        V2MemberEntity member = new V2MemberEntity();
        member.setAccountId(user.getId());
        member.setDeviceId(deviceId);
        member.setDisplayName(required(request.getDisplayName()));
        member.setRole("owner");
        member.setMemberType(StringUtils.defaultIfBlank(normalizeMemberType(request.getMemberType()), "owner"));
        member.setStatus(STATUS_ACTIVE);
        v2MemberDao.insert(member);

        String speakerRef = "local:" + sha256((user.getId() + ":" + deviceId + ":" + audioHash).getBytes(StandardCharsets.UTF_8));
        V2VoiceprintEntity voiceprint = new V2VoiceprintEntity();
        voiceprint.setAccountId(user.getId());
        voiceprint.setMemberId(member.getId());
        voiceprint.setDeviceId(deviceId);
        voiceprint.setProvider(PROVIDER_LOCAL_FAKE);
        voiceprint.setSpeakerRef(speakerRef);
        voiceprint.setEmbeddingHash(audioHash);
        voiceprint.setSampleDurationMs(durationMs);
        voiceprint.setStatus(STATUS_ACTIVE);
        voiceprint.setEnrolledAt(new Date());
        v2VoiceprintDao.insert(voiceprint);

        return new V2VoiceprintEnrollResponse(member.getId(), voiceprint.getId(), STATUS_ACTIVE, speakerRef);
    }

    @Override
    public List<V2VoiceprintCacheEntry> activeCacheForDevice(String deviceId) {
        String normalizedDeviceId = required(deviceId);
        V2DeviceBindingEntity binding = activeBindingByDevice(normalizedDeviceId);
        if (binding == null || binding.getAccountId() == null) {
            return List.of();
        }
        V2AccountEntity account = v2AccountDao.selectById(binding.getAccountId());
        if (account != null && ACCOUNT_STATUS_DELETED.equalsIgnoreCase(account.getStatus())) {
            return List.of();
        }
        List<V2VoiceprintEntity> voiceprints = v2VoiceprintDao.selectList(new LambdaQueryWrapper<V2VoiceprintEntity>()
                .eq(V2VoiceprintEntity::getAccountId, binding.getAccountId())
                .eq(V2VoiceprintEntity::getDeviceId, normalizedDeviceId)
                .eq(V2VoiceprintEntity::getStatus, STATUS_ACTIVE)
                .orderByAsc(V2VoiceprintEntity::getId));
        if (voiceprints == null || voiceprints.isEmpty()) {
            return List.of();
        }
        List<Long> memberIds = voiceprints.stream()
                .map(V2VoiceprintEntity::getMemberId)
                .distinct()
                .toList();
        Map<Long, V2MemberEntity> members = v2MemberDao.selectBatchIds(memberIds).stream()
                .collect(Collectors.toMap(V2MemberEntity::getId, member -> member));
        return voiceprints.stream()
                .map(voiceprint -> {
                    V2MemberEntity member = members.get(voiceprint.getMemberId());
                    return new V2VoiceprintCacheEntry(
                            voiceprint.getMemberId(),
                            member == null ? "" : member.getDisplayName(),
                            member == null ? "" : member.getMemberType(),
                            voiceprint.getSpeakerRef(),
                            voiceprint.getEmbeddingHash(),
                            voiceprint.getStatus(),
                            voiceprint.getExpiresAt());
                })
                .toList();
    }

    private void ensureActiveBinding(Long accountId, String deviceId) {
        V2DeviceBindingEntity binding = v2DeviceBindingDao.selectOne(new LambdaQueryWrapper<V2DeviceBindingEntity>()
                .eq(V2DeviceBindingEntity::getAccountId, accountId)
                .eq(V2DeviceBindingEntity::getDeviceId, deviceId)
                .eq(V2DeviceBindingEntity::getBindingStatus, BINDING_STATUS_ACTIVE)
                .orderByDesc(V2DeviceBindingEntity::getUpdatedAt)
                .orderByDesc(V2DeviceBindingEntity::getId)
                .last("limit 1"));
        if (binding == null) {
            throw new RenException(ErrorCode.DEVICE_NOT_EXIST);
        }
    }

    private V2DeviceBindingEntity activeBindingByDevice(String deviceId) {
        return v2DeviceBindingDao.selectOne(new LambdaQueryWrapper<V2DeviceBindingEntity>()
                .eq(V2DeviceBindingEntity::getDeviceId, deviceId)
                .eq(V2DeviceBindingEntity::getBindingStatus, BINDING_STATUS_ACTIVE)
                .orderByDesc(V2DeviceBindingEntity::getUpdatedAt)
                .orderByDesc(V2DeviceBindingEntity::getId)
                .last("limit 1"));
    }

    private static int requireEnrollmentDuration(Integer sampleDurationMs) {
        int durationMs = sampleDurationMs == null ? 0 : sampleDurationMs;
        if (durationMs < MIN_SAMPLE_DURATION_MS || durationMs > MAX_SAMPLE_DURATION_MS) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        return durationMs;
    }

    private static byte[] decodeAudio(String audioBase64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(required(audioBase64));
            if (decoded.length == 0) {
                throw new RenException(ErrorCode.PARAMS_GET_ERROR);
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
    }

    private static String normalizeMemberType(String value) {
        String text = StringUtils.trimToNull(value);
        if (text == null) {
            return null;
        }
        String normalized = text.toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "owner", "member", "child" -> normalized;
            default -> throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        };
    }

    private static String required(String value) {
        String text = StringUtils.trimToNull(value);
        if (text == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        return text;
    }

    private UserDetail requireUser() {
        UserDetail user = SecurityUser.getUser();
        if (user == null || user.getId() == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        V2AccountEntity account = v2AccountDao.selectById(user.getId());
        if (account != null && ACCOUNT_STATUS_DELETED.equalsIgnoreCase(account.getStatus())) {
            throw new RenException(ErrorCode.ACCOUNT_DISABLE);
        }
        return user;
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
