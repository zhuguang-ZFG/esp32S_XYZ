package xiaozhi.modules.appv2.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

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
import xiaozhi.modules.appv2.dao.V2DeviceTransferRequestDao;
import xiaozhi.modules.appv2.dao.V2MemberDao;
import xiaozhi.modules.appv2.dao.V2VoiceprintDao;
import xiaozhi.modules.appv2.dto.V2DeviceTransferRequest;
import xiaozhi.modules.appv2.dto.V2DeviceTransferResponse;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2DeviceTransferRequestEntity;
import xiaozhi.modules.appv2.entity.V2MemberEntity;
import xiaozhi.modules.appv2.entity.V2VoiceprintEntity;
import xiaozhi.modules.appv2.service.DeviceServerMotionGateway;
import xiaozhi.modules.appv2.service.DeviceTransferService;
import xiaozhi.modules.appv2.service.ProductNotificationOutboxService;
import xiaozhi.modules.security.user.SecurityUser;

@Service
@AllArgsConstructor
public class DeviceTransferServiceImpl implements DeviceTransferService {
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_ACCEPTED = "accepted";
    private static final String STATUS_CANCELLED = "cancelled";
    private static final String STATUS_DELETED = "deleted";
    private static final String BINDING_STATUS_ACTIVE = "active";
    private static final String BINDING_STATUS_TRANSFERRED = "transferred";
    private static final int AUDIT_RETENTION_DAYS = 180;

    private final V2AccountDao v2AccountDao;
    private final V2DeviceBindingDao v2DeviceBindingDao;
    private final V2DeviceTransferRequestDao v2DeviceTransferRequestDao;
    private final V2MemberDao v2MemberDao;
    private final V2VoiceprintDao v2VoiceprintDao;
    private final DeviceServerMotionGateway deviceServerMotionGateway;
    private final ProductNotificationOutboxService productNotificationOutboxService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2DeviceTransferResponse requestTransfer(String deviceId, V2DeviceTransferRequest request) {
        UserDetail user = requireUser();
        String normalizedDeviceId = required(deviceId);
        String targetUnionid = required(request.getTargetUnionid());
        ensureActiveBinding(user.getId(), normalizedDeviceId);
        V2AccountEntity target = v2AccountDao.selectOne(new LambdaQueryWrapper<V2AccountEntity>()
                .eq(V2AccountEntity::getUnionid, targetUnionid)
                .eq(V2AccountEntity::getStatus, STATUS_ACTIVE)
                .last("limit 1"));
        if (target == null || target.getId() == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        if (user.getId().equals(target.getId())) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }

        V2DeviceTransferRequestEntity transfer = new V2DeviceTransferRequestEntity();
        transfer.setDeviceId(normalizedDeviceId);
        transfer.setSourceAccountId(user.getId());
        transfer.setTargetAccountId(target.getId());
        transfer.setTargetUnionid(targetUnionid);
        transfer.setStatus(STATUS_PENDING);
        transfer.setRequestedAt(new Date());
        v2DeviceTransferRequestDao.insert(transfer);
        productNotificationOutboxService.enqueuePendingDeviceTransfer(
                target.getId(),
                normalizedDeviceId,
                transfer.getId());
        return toResponse(transfer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2DeviceTransferResponse acceptTransfer(Long transferId) {
        UserDetail user = requireUser();
        if (transferId == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        V2DeviceTransferRequestEntity transfer = v2DeviceTransferRequestDao.selectOne(
                new LambdaQueryWrapper<V2DeviceTransferRequestEntity>()
                        .eq(V2DeviceTransferRequestEntity::getId, transferId)
                        .eq(V2DeviceTransferRequestEntity::getTargetAccountId, user.getId())
                        .eq(V2DeviceTransferRequestEntity::getStatus, STATUS_PENDING)
                        .last("limit 1"));
        if (transfer == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        V2DeviceBindingEntity sourceBinding =
                ensureActiveBinding(transfer.getSourceAccountId(), transfer.getDeviceId());
        Date now = new Date();

        sourceBinding.setBindingStatus(BINDING_STATUS_TRANSFERRED);
        sourceBinding.setIsPrimary(Boolean.FALSE);
        sourceBinding.setUnboundAt(now);
        sourceBinding.setUpdatedAt(now);
        v2DeviceBindingDao.updateById(sourceBinding);

        V2DeviceBindingEntity targetBinding = new V2DeviceBindingEntity();
        targetBinding.setAccountId(user.getId());
        targetBinding.setDeviceId(transfer.getDeviceId());
        targetBinding.setBindingStatus(BINDING_STATUS_ACTIVE);
        targetBinding.setIsPrimary(Boolean.TRUE);
        targetBinding.setBoundAt(now);
        v2DeviceBindingDao.insert(targetBinding);

        transfer.setStatus(STATUS_ACCEPTED);
        transfer.setAcceptedAt(now);
        v2DeviceTransferRequestDao.updateById(transfer);
        productNotificationOutboxService.resolveDeviceTransfer(transfer.getId());

        clearOldMembersAndVoiceprints(transfer.getSourceAccountId(), transfer.getDeviceId(), now);
        clearPrimarySession(transfer.getSourceAccountId());
        deviceServerMotionGateway.clearVoiceprintCache(transfer.getDeviceId(), "device_transfer");
        return toResponse(transfer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2DeviceTransferResponse cancelTransfer(Long transferId) {
        UserDetail user = requireUser();
        if (transferId == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        V2DeviceTransferRequestEntity transfer = v2DeviceTransferRequestDao.selectOne(
                new LambdaQueryWrapper<V2DeviceTransferRequestEntity>()
                        .eq(V2DeviceTransferRequestEntity::getId, transferId)
                        .eq(V2DeviceTransferRequestEntity::getSourceAccountId, user.getId())
                        .eq(V2DeviceTransferRequestEntity::getStatus, STATUS_PENDING)
                        .last("limit 1"));
        if (transfer == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }

        transfer.setStatus(STATUS_CANCELLED);
        v2DeviceTransferRequestDao.updateById(transfer);
        productNotificationOutboxService.cancelDeviceTransfer(transfer.getId());
        return toResponse(transfer);
    }

    @Override
    public List<V2DeviceTransferResponse> listPendingIncomingTransfers() {
        UserDetail user = requireUser();
        return v2DeviceTransferRequestDao.selectList(new LambdaQueryWrapper<V2DeviceTransferRequestEntity>()
                .eq(V2DeviceTransferRequestEntity::getTargetAccountId, user.getId())
                .eq(V2DeviceTransferRequestEntity::getStatus, STATUS_PENDING)
                .orderByDesc(V2DeviceTransferRequestEntity::getRequestedAt))
                .stream()
                .map(DeviceTransferServiceImpl::toResponse)
                .toList();
    }

    private V2DeviceBindingEntity ensureActiveBinding(Long accountId, String deviceId) {
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
        return binding;
    }

    private void clearOldMembersAndVoiceprints(Long sourceAccountId, String deviceId, Date now) {
        v2MemberDao.update(null, new UpdateWrapper<V2MemberEntity>()
                .eq("account_id", sourceAccountId)
                .eq("device_id", deviceId)
                .set("status", STATUS_DELETED));
        List<V2VoiceprintEntity> voiceprints = v2VoiceprintDao.selectList(new LambdaQueryWrapper<V2VoiceprintEntity>()
                .eq(V2VoiceprintEntity::getAccountId, sourceAccountId)
                .eq(V2VoiceprintEntity::getDeviceId, deviceId)
                .ne(V2VoiceprintEntity::getStatus, STATUS_DELETED));
        for (V2VoiceprintEntity voiceprint : voiceprints) {
            voiceprint.setStatus(STATUS_DELETED);
            voiceprint.setSpeakerRef("deleted:" + sourceAccountId + ":" + voiceprint.getId());
            voiceprint.setEmbeddingHash("0".repeat(64));
            voiceprint.setExpiresAt(now);
            voiceprint.setDeletedAt(now);
            voiceprint.setAuditRetainUntil(Date.from(now.toInstant().plus(AUDIT_RETENTION_DAYS, ChronoUnit.DAYS)));
            v2VoiceprintDao.updateById(voiceprint);
        }
    }

    private void clearPrimarySession(Long accountId) {
        V2AccountEntity account = v2AccountDao.selectById(accountId);
        if (account == null) {
            return;
        }
        account.setPrimarySessionId(null);
        account.setPrimarySessionClaimedAt(null);
        account.setUpdatedAt(Date.from(Instant.now()));
        v2AccountDao.updateById(account);
    }

    private static V2DeviceTransferResponse toResponse(V2DeviceTransferRequestEntity transfer) {
        return new V2DeviceTransferResponse(
                transfer.getId(),
                transfer.getDeviceId(),
                transfer.getSourceAccountId(),
                transfer.getTargetAccountId(),
                transfer.getStatus());
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
        if (account != null && STATUS_DELETED.equalsIgnoreCase(account.getStatus())) {
            throw new RenException(ErrorCode.ACCOUNT_DISABLE);
        }
        return user;
    }
}
