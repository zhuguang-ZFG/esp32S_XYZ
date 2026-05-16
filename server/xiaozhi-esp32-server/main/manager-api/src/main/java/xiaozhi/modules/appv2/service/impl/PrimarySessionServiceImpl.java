package xiaozhi.modules.appv2.service.impl;

import java.util.Date;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.AllArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.appv2.dao.V2AccountDao;
import xiaozhi.modules.appv2.dao.V2DeviceBindingDao;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.service.PrimarySessionException;
import xiaozhi.modules.appv2.service.PrimarySessionService;

@Service
@AllArgsConstructor
public class PrimarySessionServiceImpl implements PrimarySessionService {
    public static final String ERROR_NOT_PRIMARY = "E_NOT_PRIMARY";
    static final long PRIMARY_SESSION_LEASE_MS = 60_000L;

    private static final String BINDING_STATUS_ACTIVE = "active";

    private final V2AccountDao v2AccountDao;
    private final V2DeviceBindingDao v2DeviceBindingDao;

    @Override
    public void requirePrimaryForWrite(Long accountId, String deviceId, String sessionId) {
        V2DeviceBindingEntity binding = requireActiveBinding(accountId, deviceId);
        V2AccountEntity account = v2AccountDao.selectById(binding.getAccountId());
        releaseExpiredPrimarySession(account);
        if (account == null || StringUtils.isBlank(account.getPrimarySessionId())) {
            throw new PrimarySessionException(ERROR_NOT_PRIMARY, "primary session is not claimed");
        }
        if (!Objects.equals(account.getPrimarySessionId(), StringUtils.trimToNull(sessionId))) {
            throw new PrimarySessionException(ERROR_NOT_PRIMARY, "current session is not primary");
        }
    }

    @Override
    public void requireVoiceAllowedForWrite(Long accountId, String deviceId) {
        V2DeviceBindingEntity binding = requireActiveBinding(accountId, deviceId);
        V2AccountEntity account = v2AccountDao.selectById(binding.getAccountId());
        releaseExpiredPrimarySession(account);
        if (account != null && StringUtils.isNotBlank(account.getPrimarySessionId())) {
            throw new PrimarySessionException(ERROR_NOT_PRIMARY, "primary session blocks voice write");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claimPrimary(Long accountId, String deviceId, String sessionId) {
        V2DeviceBindingEntity binding = requireActiveBinding(accountId, deviceId);
        String normalizedSessionId = StringUtils.trimToNull(sessionId);
        if (normalizedSessionId == null) {
            throw new PrimarySessionException(ERROR_NOT_PRIMARY, "missing session id");
        }
        V2AccountEntity account = v2AccountDao.selectById(binding.getAccountId());
        if (account == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        Date now = new Date();
        account.setPrimarySessionId(normalizedSessionId);
        account.setPrimarySessionClaimedAt(now);
        account.setUpdatedAt(now);
        v2AccountDao.updateById(account);
    }

    private void releaseExpiredPrimarySession(V2AccountEntity account) {
        if (account == null || StringUtils.isBlank(account.getPrimarySessionId())) {
            return;
        }
        Date claimedAt = account.getPrimarySessionClaimedAt();
        if (claimedAt == null || System.currentTimeMillis() - claimedAt.getTime() <= PRIMARY_SESSION_LEASE_MS) {
            return;
        }
        account.setPrimarySessionId(null);
        account.setPrimarySessionClaimedAt(null);
        account.setUpdatedAt(new Date());
        v2AccountDao.updateById(account);
    }

    private V2DeviceBindingEntity requireActiveBinding(Long accountId, String deviceId) {
        String normalizedDeviceId = StringUtils.trimToNull(deviceId);
        if (accountId == null || normalizedDeviceId == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        V2DeviceBindingEntity binding = v2DeviceBindingDao.selectOne(new LambdaQueryWrapper<V2DeviceBindingEntity>()
                .eq(V2DeviceBindingEntity::getAccountId, accountId)
                .eq(V2DeviceBindingEntity::getDeviceId, normalizedDeviceId)
                .eq(V2DeviceBindingEntity::getBindingStatus, BINDING_STATUS_ACTIVE)
                .orderByDesc(V2DeviceBindingEntity::getUpdatedAt)
                .orderByDesc(V2DeviceBindingEntity::getId)
                .last("limit 1"));
        if (binding == null) {
            throw new RenException(ErrorCode.DEVICE_NOT_EXIST);
        }
        return binding;
    }
}
