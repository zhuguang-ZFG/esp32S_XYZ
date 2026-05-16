package xiaozhi.modules.appv2.service.impl;

import java.util.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import lombok.AllArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.user.UserDetail;
import xiaozhi.modules.appv2.dao.V2AccountDao;
import xiaozhi.modules.appv2.dao.V2DeviceBindingDao;
import xiaozhi.modules.appv2.dao.V2MemberDao;
import xiaozhi.modules.appv2.dao.V2VoiceprintDao;
import xiaozhi.modules.appv2.dto.V2DeletionResponse;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2MemberEntity;
import xiaozhi.modules.appv2.entity.V2VoiceprintEntity;
import xiaozhi.modules.appv2.service.PrivacyDeletionService;
import xiaozhi.modules.security.service.SysUserTokenService;
import xiaozhi.modules.security.user.SecurityUser;

@Service
@AllArgsConstructor
public class PrivacyDeletionServiceImpl implements PrivacyDeletionService {
    private static final String STATUS_DELETED = "deleted";
    private static final String BINDING_STATUS_UNBOUND = "unbound";
    private static final int AUDIT_RETENTION_DAYS = 180;

    private final V2AccountDao v2AccountDao;
    private final V2DeviceBindingDao v2DeviceBindingDao;
    private final V2MemberDao v2MemberDao;
    private final V2VoiceprintDao v2VoiceprintDao;
    private final SysUserTokenService sysUserTokenService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2DeletionResponse deleteVoiceprint(Long voiceprintId) {
        UserDetail user = requireUser();
        if (voiceprintId == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        V2VoiceprintEntity voiceprint = v2VoiceprintDao.selectOne(new LambdaQueryWrapper<V2VoiceprintEntity>()
                .eq(V2VoiceprintEntity::getId, voiceprintId)
                .eq(V2VoiceprintEntity::getAccountId, user.getId())
                .last("limit 1"));
        if (voiceprint == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        return new V2DeletionResponse(STATUS_DELETED, anonymizeVoiceprint(voiceprint, new Date()), AUDIT_RETENTION_DAYS);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2DeletionResponse deleteAccount() {
        UserDetail user = requireUser();
        Date now = new Date();
        int affectedRows = 0;

        V2AccountEntity account = v2AccountDao.selectById(user.getId());
        if (account != null) {
            account.setStatus(STATUS_DELETED);
            account.setOpenid(null);
            account.setDisplayName("deleted-account-" + user.getId());
            account.setPrimarySessionId(null);
            account.setPrimarySessionClaimedAt(null);
            account.setDeletedAt(now);
            account.setAuditRetainUntil(auditRetainUntil(now));
            affectedRows += v2AccountDao.updateById(account);
        }

        affectedRows += v2DeviceBindingDao.update(null, new UpdateWrapper<V2DeviceBindingEntity>()
                .eq("account_id", user.getId())
                .set("binding_status", BINDING_STATUS_UNBOUND)
                .set("unbound_at", now));
        affectedRows += v2MemberDao.update(null, new UpdateWrapper<V2MemberEntity>()
                .eq("account_id", user.getId())
                .set("status", STATUS_DELETED)
                .set("display_name", "deleted-member"));
        List<V2VoiceprintEntity> voiceprints = v2VoiceprintDao.selectList(new LambdaQueryWrapper<V2VoiceprintEntity>()
                .eq(V2VoiceprintEntity::getAccountId, user.getId())
                .ne(V2VoiceprintEntity::getStatus, STATUS_DELETED));
        for (V2VoiceprintEntity voiceprint : voiceprints) {
            affectedRows += anonymizeVoiceprint(voiceprint, now);
        }
        sysUserTokenService.logout(user.getId());

        return new V2DeletionResponse(STATUS_DELETED, affectedRows, AUDIT_RETENTION_DAYS);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int purgeExpiredRetention(Date now) {
        Objects.requireNonNull(now, "now");
        int affectedRows = 0;
        affectedRows += v2VoiceprintDao.delete(new LambdaQueryWrapper<V2VoiceprintEntity>()
                .eq(V2VoiceprintEntity::getStatus, STATUS_DELETED)
                .isNotNull(V2VoiceprintEntity::getAuditRetainUntil)
                .le(V2VoiceprintEntity::getAuditRetainUntil, now));
        affectedRows += v2AccountDao.update(null, new UpdateWrapper<V2AccountEntity>()
                .eq("status", STATUS_DELETED)
                .isNotNull("audit_retain_until")
                .le("audit_retain_until", now)
                .set("display_name", null)
                .set("deleted_at", null)
                .set("audit_retain_until", null));
        return affectedRows;
    }

    private int anonymizeVoiceprint(V2VoiceprintEntity voiceprint, Date deletedAt) {
        voiceprint.setStatus(STATUS_DELETED);
        voiceprint.setSpeakerRef(deletedSpeakerRef(voiceprint.getAccountId(), String.valueOf(voiceprint.getId())));
        voiceprint.setEmbeddingHash(deletedEmbeddingHash());
        voiceprint.setExpiresAt(deletedAt);
        voiceprint.setDeletedAt(deletedAt);
        voiceprint.setAuditRetainUntil(auditRetainUntil(deletedAt));
        return v2VoiceprintDao.updateById(voiceprint);
    }

    private static Date auditRetainUntil(Date deletedAt) {
        Instant instant = deletedAt.toInstant().plus(AUDIT_RETENTION_DAYS, ChronoUnit.DAYS);
        return Date.from(instant);
    }

    private static String deletedSpeakerRef(Long accountId, String suffix) {
        return "deleted:" + accountId + ":" + StringUtils.defaultIfBlank(suffix, "voiceprint");
    }

    private static String deletedEmbeddingHash() {
        return "0".repeat(64);
    }

    private static UserDetail requireUser() {
        UserDetail user = SecurityUser.getUser();
        if (user == null || user.getId() == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        return user;
    }
}
