package xiaozhi.modules.appv2.service.safety;

import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.AllArgsConstructor;
import xiaozhi.modules.appv2.dao.V2SafetyAuditDao;
import xiaozhi.modules.appv2.entity.V2SafetyAuditEntity;

@Service
@AllArgsConstructor
public class SafetyAuditService {
    private static final int MAX_REASON_LENGTH = 255;
    private static final int RETENTION_DAYS = 180;

    private final V2SafetyAuditDao v2SafetyAuditDao;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBusinessReject(
            Long accountId,
            String deviceId,
            String capability,
            SafetyValidationException error) {
        insertAudit(accountId, deviceId, capability, error.getErrorCode() + ":" + error.getReason());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordU1Reject(
            Long accountId,
            String deviceId,
            String capability,
            String errorCode,
            String errorMessage) {
        insertAudit(accountId, deviceId, capability, "U1:" + errorCode + ":" + StringUtils.defaultString(errorMessage));
    }

    @Transactional(rollbackFor = Exception.class)
    public int purgeExpired(Date now) {
        Objects.requireNonNull(now, "now");
        Date cutoff = Date.from(now.toInstant().minus(RETENTION_DAYS, ChronoUnit.DAYS));
        return v2SafetyAuditDao.delete(new LambdaQueryWrapper<V2SafetyAuditEntity>()
                .lt(V2SafetyAuditEntity::getTs, cutoff));
    }

    private void insertAudit(Long accountId, String deviceId, String capability, String reason) {
        V2SafetyAuditEntity audit = new V2SafetyAuditEntity();
        audit.setAccountId(accountId);
        audit.setDeviceId(deviceId);
        audit.setCapability(capability);
        audit.setReason(StringUtils.left(reason, MAX_REASON_LENGTH));
        audit.setTs(new Date());
        v2SafetyAuditDao.insert(audit);
    }
}
