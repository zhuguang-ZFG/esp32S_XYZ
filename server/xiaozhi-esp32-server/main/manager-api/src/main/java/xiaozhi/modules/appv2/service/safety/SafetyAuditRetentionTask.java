package xiaozhi.modules.appv2.service.safety;

import java.util.Date;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@AllArgsConstructor
@Slf4j
public class SafetyAuditRetentionTask {
    private final SafetyAuditService safetyAuditService;

    @Scheduled(cron = "${appv2.safety.audit.retention-cron:0 20 3 * * *}")
    public void purgeExpiredSafetyAudit() {
        try {
            int deleted = safetyAuditService.purgeExpired(new Date());
            log.info("purged expired safety_audit rows count={}", deleted);
        } catch (Exception e) {
            log.error("safety_audit retention task failed", e);
        }
    }
}
