package xiaozhi.modules.appv2.service;

import java.util.Date;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@AllArgsConstructor
@Slf4j
public class PrivacyRetentionCleanupTask {
    private final PrivacyDeletionService privacyDeletionService;

    @Scheduled(cron = "${appv2.privacy.retention-cleanup-cron:0 50 3 * * *}")
    public void purgeExpiredPrivacyRetention() {
        try {
            int affectedRows = privacyDeletionService.purgeExpiredRetention(new Date());
            log.info("purged expired privacy retention rows count={}", affectedRows);
        } catch (Exception e) {
            log.error("privacy retention cleanup task failed", e);
        }
    }
}
