package xiaozhi.modules.appv2.service.contentaudit;

import java.util.Date;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@AllArgsConstructor
@Slf4j
public class ContentAuditRetentionTask {
    private final ContentAuditLogService contentAuditLogService;

    @Scheduled(cron = "${appv2.content.audit.retention-cron:0 35 3 * * *}")
    public void purgeExpiredContentAudit() {
        try {
            int deleted = contentAuditLogService.purgeExpired(new Date());
            log.info("purged expired content_audit rows count={}", deleted);
        } catch (Exception e) {
            log.error("content_audit retention task failed", e);
        }
    }
}
