package xiaozhi.modules.appv2.service.impl;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.AllArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.user.UserDetail;
import xiaozhi.modules.appv2.dao.V2AccountDao;
import xiaozhi.modules.appv2.dao.V2ActivationCodeDao;
import xiaozhi.modules.appv2.dao.V2DeviceBindingDao;
import xiaozhi.modules.appv2.dao.V2DeviceDao;
import xiaozhi.modules.appv2.dao.V2DeviceRmaEventDao;
import xiaozhi.modules.appv2.dto.V2DeviceRmaRequest;
import xiaozhi.modules.appv2.dto.V2DeviceRmaResponse;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2ActivationCodeEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2DeviceEntity;
import xiaozhi.modules.appv2.entity.V2DeviceRmaEventEntity;
import xiaozhi.modules.appv2.service.DeviceRmaService;
import xiaozhi.modules.security.user.SecurityUser;

@Service
@AllArgsConstructor
public class DeviceRmaServiceImpl implements DeviceRmaService {
    private static final String DEVICE_BOUND = "bound";
    private static final String DEVICE_RMA_IN_PROGRESS = "rma_in_progress";
    private static final String DEVICE_RETURNED = "returned";
    private static final String DEVICE_DISPOSED = "disposed";
    private static final String DEVICE_PROVISIONED_UNBOUND = "provisioned_unbound";
    private static final String BINDING_ACTIVE = "active";
    private static final String BINDING_RMA_IN_PROGRESS = "rma_in_progress";
    private static final String BINDING_RETURNED = "returned";
    private static final String BINDING_DISPOSED = "disposed";
    private static final String ACTIVATION_BOUND = "bound";
    private static final String ACTIVATION_PROVISIONED = "provisioned";
    private static final String ACTIVATION_REVOKED = "revoked";
    private static final String ACCOUNT_STATUS_DELETED = "deleted";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final V2DeviceDao v2DeviceDao;
    private final V2DeviceBindingDao v2DeviceBindingDao;
    private final V2ActivationCodeDao v2ActivationCodeDao;
    private final V2DeviceRmaEventDao v2DeviceRmaEventDao;
    private final V2AccountDao v2AccountDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2DeviceRmaResponse startRepair(String deviceId, V2DeviceRmaRequest request) {
        UserDetail user = requireUser();
        String normalizedDeviceId = required(deviceId);
        V2DeviceEntity device = requireDevice(normalizedDeviceId, DEVICE_BOUND);
        V2DeviceBindingEntity binding = requireBinding(normalizedDeviceId, BINDING_ACTIVE);
        String fromDeviceStatus = device.getStatus();
        String fromBindingStatus = binding.getBindingStatus();
        Date now = new Date();

        device.setStatus(DEVICE_RMA_IN_PROGRESS);
        v2DeviceDao.updateById(device);
        binding.setBindingStatus(BINDING_RMA_IN_PROGRESS);
        binding.setIsPrimary(Boolean.FALSE);
        binding.setUnboundAt(now);
        binding.setUpdatedAt(now);
        v2DeviceBindingDao.updateById(binding);
        recordRmaEvent(user, "repair_start", device, fromDeviceStatus, binding, fromBindingStatus, request);
        return response(device, binding, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2DeviceRmaResponse completeRepair(String deviceId, V2DeviceRmaRequest request) {
        UserDetail user = requireUser();
        String normalizedDeviceId = required(deviceId);
        V2DeviceEntity device = requireDevice(normalizedDeviceId, DEVICE_RMA_IN_PROGRESS);
        V2DeviceBindingEntity binding = requireBinding(normalizedDeviceId, BINDING_RMA_IN_PROGRESS);
        String fromDeviceStatus = device.getStatus();
        String fromBindingStatus = binding.getBindingStatus();

        device.setStatus(DEVICE_BOUND);
        v2DeviceDao.updateById(device);
        binding.setBindingStatus(BINDING_ACTIVE);
        binding.setIsPrimary(Boolean.TRUE);
        binding.setUnboundAt(null);
        binding.setUpdatedAt(new Date());
        v2DeviceBindingDao.updateById(binding);
        recordRmaEvent(user, "repair_complete", device, fromDeviceStatus, binding, fromBindingStatus, request);
        return response(device, binding, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2DeviceRmaResponse confirmReturn(String deviceId, V2DeviceRmaRequest request) {
        UserDetail user = requireUser();
        String normalizedDeviceId = required(deviceId);
        V2DeviceEntity device = requireDevice(normalizedDeviceId, DEVICE_BOUND);
        V2DeviceBindingEntity binding = requireBinding(normalizedDeviceId, BINDING_ACTIVE);
        String fromDeviceStatus = device.getStatus();
        String fromBindingStatus = binding.getBindingStatus();
        Date now = new Date();

        device.setStatus(DEVICE_RETURNED);
        v2DeviceDao.updateById(device);
        binding.setBindingStatus(BINDING_RETURNED);
        binding.setIsPrimary(Boolean.FALSE);
        binding.setUnboundAt(now);
        binding.setUpdatedAt(now);
        v2DeviceBindingDao.updateById(binding);
        updateActivationStatus(device, ACTIVATION_REVOKED, null, false);
        recordRmaEvent(user, "return_confirm", device, fromDeviceStatus, binding, fromBindingStatus, request);
        return response(device, binding, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2DeviceRmaResponse restockReturned(String deviceId, V2DeviceRmaRequest request) {
        UserDetail user = requireUser();
        String normalizedDeviceId = required(deviceId);
        V2DeviceEntity device = requireDevice(normalizedDeviceId, DEVICE_RETURNED);
        V2DeviceBindingEntity binding = requireBinding(normalizedDeviceId, BINDING_RETURNED);
        String fromDeviceStatus = device.getStatus();
        String fromBindingStatus = binding.getBindingStatus();
        requireFactoryCleaningEvidence(request);
        String activationCode = StringUtils.trimToNull(request == null ? null : request.getActivationCode());
        if (activationCode == null) {
            activationCode = generateActivationCode();
        }

        device.setStatus(DEVICE_PROVISIONED_UNBOUND);
        v2DeviceDao.updateById(device);
        updateActivationStatus(device, ACTIVATION_PROVISIONED, activationCode, true);
        recordRmaEvent(user, "restock", device, fromDeviceStatus, binding, fromBindingStatus, request);
        return response(device, binding, activationCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2DeviceRmaResponse disposeDevice(String deviceId, V2DeviceRmaRequest request) {
        UserDetail user = requireUser();
        String normalizedDeviceId = required(deviceId);
        V2DeviceEntity device = requireDevice(normalizedDeviceId, null);
        V2DeviceBindingEntity binding = latestKnownBinding(normalizedDeviceId);
        String fromDeviceStatus = device.getStatus();
        String fromBindingStatus = binding == null ? null : binding.getBindingStatus();
        requireFactoryCleaningEvidence(request);
        Date now = new Date();

        device.setStatus(DEVICE_DISPOSED);
        v2DeviceDao.updateById(device);
        if (binding != null) {
            binding.setBindingStatus(BINDING_DISPOSED);
            binding.setIsPrimary(Boolean.FALSE);
            if (binding.getUnboundAt() == null) {
                binding.setUnboundAt(now);
            }
            binding.setUpdatedAt(now);
            v2DeviceBindingDao.updateById(binding);
        }
        updateActivationStatus(device, ACTIVATION_REVOKED, null, false);
        recordRmaEvent(user, "dispose", device, fromDeviceStatus, binding, fromBindingStatus, request);
        return response(device, binding, null);
    }

    @Override
    public List<V2DeviceRmaEventEntity> listAuditEvents(String deviceId) {
        requireUser();
        String normalizedDeviceId = required(deviceId);
        requireDevice(normalizedDeviceId, null);
        return v2DeviceRmaEventDao.selectList(new LambdaQueryWrapper<V2DeviceRmaEventEntity>()
                .eq(V2DeviceRmaEventEntity::getDeviceId, normalizedDeviceId)
                .orderByDesc(V2DeviceRmaEventEntity::getCreatedAt)
                .last("limit 50"));
    }

    private V2DeviceEntity requireDevice(String deviceId, String expectedStatus) {
        V2DeviceEntity device = v2DeviceDao.selectById(deviceId);
        if (device == null) {
            throw new RenException(ErrorCode.DEVICE_NOT_EXIST);
        }
        if (expectedStatus != null && !expectedStatus.equals(device.getStatus())) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        return device;
    }

    private V2DeviceBindingEntity requireBinding(String deviceId, String status) {
        V2DeviceBindingEntity binding = v2DeviceBindingDao.selectOne(new LambdaQueryWrapper<V2DeviceBindingEntity>()
                .eq(V2DeviceBindingEntity::getDeviceId, deviceId)
                .eq(V2DeviceBindingEntity::getBindingStatus, status)
                .orderByDesc(V2DeviceBindingEntity::getUpdatedAt)
                .orderByDesc(V2DeviceBindingEntity::getId)
                .last("limit 1"));
        if (binding == null) {
            throw new RenException(ErrorCode.DEVICE_NOT_EXIST);
        }
        return binding;
    }

    private V2DeviceBindingEntity latestKnownBinding(String deviceId) {
        return v2DeviceBindingDao.selectOne(new LambdaQueryWrapper<V2DeviceBindingEntity>()
                .eq(V2DeviceBindingEntity::getDeviceId, deviceId)
                .orderByDesc(V2DeviceBindingEntity::getUpdatedAt)
                .orderByDesc(V2DeviceBindingEntity::getId)
                .last("limit 1"));
    }

    private void updateActivationStatus(
            V2DeviceEntity device,
            String status,
            String activationCode,
            boolean clearUsedAt) {
        V2ActivationCodeEntity activation = v2ActivationCodeDao.selectOne(new LambdaQueryWrapper<V2ActivationCodeEntity>()
                .eq(V2ActivationCodeEntity::getDeviceId, device.getId())
                .orderByDesc(V2ActivationCodeEntity::getUpdatedAt)
                .orderByDesc(V2ActivationCodeEntity::getId)
                .last("limit 1"));
        if (activation == null) {
            activation = new V2ActivationCodeEntity();
            activation.setDeviceId(device.getId());
            activation.setDeviceSn(device.getDeviceSn());
            activation.setActivationCode(StringUtils.defaultIfBlank(activationCode, generateActivationCode()));
            activation.setStatus(status);
            if (!clearUsedAt) {
                activation.setUsedAt(new Date());
            }
            v2ActivationCodeDao.insert(activation);
            return;
        }
        if (StringUtils.isNotBlank(activationCode)) {
            activation.setActivationCode(activationCode);
        }
        activation.setStatus(status);
        if (clearUsedAt) {
            activation.setUsedAt(null);
        } else if (ACTIVATION_BOUND.equals(status) && activation.getUsedAt() == null) {
            activation.setUsedAt(new Date());
        }
        v2ActivationCodeDao.updateById(activation);
    }

    private static V2DeviceRmaResponse response(
            V2DeviceEntity device,
            V2DeviceBindingEntity binding,
            String activationCode) {
        return new V2DeviceRmaResponse(
                device.getId(),
                binding == null ? null : binding.getAccountId(),
                device.getStatus(),
                binding == null ? null : binding.getBindingStatus(),
                activationCode);
    }

    private void recordRmaEvent(
            UserDetail user,
            String action,
            V2DeviceEntity device,
            String fromDeviceStatus,
            V2DeviceBindingEntity binding,
            String fromBindingStatus,
            V2DeviceRmaRequest request) {
        V2DeviceRmaEventEntity event = new V2DeviceRmaEventEntity();
        event.setDeviceId(device.getId());
        event.setDeviceSn(device.getDeviceSn());
        event.setOperatorAccountId(user.getId());
        event.setAction(action);
        event.setFromDeviceStatus(fromDeviceStatus);
        event.setToDeviceStatus(device.getStatus());
        event.setFromBindingStatus(fromBindingStatus);
        event.setToBindingStatus(binding == null ? null : binding.getBindingStatus());
        event.setFactoryCleaned(request == null ? null : request.getFactoryCleaned());
        event.setEvidenceRef(request == null ? null : StringUtils.trimToNull(request.getEvidenceRef()));
        event.setTicketRef(request == null ? null : StringUtils.trimToNull(request.getTicketRef()));
        event.setCreatedAt(new Date());
        v2DeviceRmaEventDao.insert(event);
    }

    private static void requireFactoryCleaningEvidence(V2DeviceRmaRequest request) {
        if (request == null
                || !Boolean.TRUE.equals(request.getFactoryCleaned())
                || StringUtils.isBlank(request.getEvidenceRef())) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
    }

    private static String generateActivationCode() {
        byte[] bytes = new byte[18];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
}
