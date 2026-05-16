package xiaozhi.modules.appv2.service.impl;

import java.util.Date;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.AllArgsConstructor;
import xiaozhi.modules.appv2.dao.V2DeviceDao;
import xiaozhi.modules.appv2.dao.V2FirmwareReleaseDao;
import xiaozhi.modules.appv2.dao.V2ProductNotificationEventDao;
import xiaozhi.modules.appv2.dao.V2TaskDao;
import xiaozhi.modules.appv2.entity.V2DeviceEntity;
import xiaozhi.modules.appv2.entity.V2FirmwareReleaseEntity;
import xiaozhi.modules.appv2.entity.V2ProductNotificationEventEntity;
import xiaozhi.modules.appv2.entity.V2TaskEntity;
import xiaozhi.modules.appv2.service.MonitoringMetricsService;

@Service
@AllArgsConstructor
public class MonitoringMetricsServiceImpl implements MonitoringMetricsService {
    private static final long TASK_WINDOW_MS = 5 * 60 * 1000L;
    private static final long OFFLINE_AFTER_MS = 10 * 60 * 1000L;
    private static final String TASK_STATUS_FAILED = "failed";
    private static final String TASK_STATUS_DONE = "done";
    private static final String RELEASE_STATUS_ACTIVE = "active";
    private static final String RELEASE_STATUS_PUBLISHED = "published";
    private static final String RELEASE_STATUS_PAUSED = "paused";
    private static final String NOTIFICATION_STATUS_PENDING = "pending";
    private static final String NOTIFICATION_STATUS_FAILED = "failed";

    private final V2TaskDao v2TaskDao;
    private final V2DeviceDao v2DeviceDao;
    private final V2FirmwareReleaseDao v2FirmwareReleaseDao;
    private final V2ProductNotificationEventDao v2ProductNotificationEventDao;

    @Override
    public String prometheusText() {
        Date now = new Date();
        Date taskWindowStart = new Date(now.getTime() - TASK_WINDOW_MS);
        Date offlineCutoff = new Date(now.getTime() - OFFLINE_AFTER_MS);
        long taskDone = countTasks(TASK_STATUS_DONE, taskWindowStart);
        long taskFailed = countTasks(TASK_STATUS_FAILED, taskWindowStart);
        long taskTerminal = taskDone + taskFailed;
        double taskFailureRatio = taskTerminal == 0L ? 0.0 : (double) taskFailed / (double) taskTerminal;
        long boundDevices = countDevicesByStatus("bound");
        long rmaInProgressDevices = countDevicesByStatus("rma_in_progress");
        long returnedDevices = countDevicesByStatus("returned");
        long disposedDevices = countDevicesByStatus("disposed");
        long offlineDevices = countOfflineDevices(offlineCutoff);
        long otaInstallCount = sumFirmwareInteger("install_count");
        long otaFailureCount = sumFirmwareInteger("failure_count");
        double otaFailureRatio = otaInstallCount == 0L ? 0.0 : (double) otaFailureCount / (double) otaInstallCount;
        long notificationPending = countNotificationEvents(NOTIFICATION_STATUS_PENDING);
        long notificationFailed = countNotificationEvents(NOTIFICATION_STATUS_FAILED);

        StringBuilder out = new StringBuilder(1024);
        metricHelp(out, "biz_task_done_total", "Completed v2 tasks in the recent 5 minute window.");
        gauge(out, "biz_task_done_total", taskDone);
        metricHelp(out, "biz_task_failed_total", "Failed v2 tasks in the recent 5 minute window.");
        gauge(out, "biz_task_failed_total", taskFailed);
        metricHelp(out, "biz_task_failure_ratio", "Failed task ratio over done+failed tasks in the recent 5 minute window.");
        gauge(out, "biz_task_failure_ratio", taskFailureRatio);
        metricHelp(out, "dev_device_bound_total", "BusinessServer devices currently marked bound.");
        gauge(out, "dev_device_bound_total", boundDevices);
        metricHelp(out, "dev_device_rma_in_progress_total", "BusinessServer devices currently in repair RMA.");
        gauge(out, "dev_device_rma_in_progress_total", rmaInProgressDevices);
        metricHelp(out, "dev_device_returned_total", "BusinessServer devices currently returned and awaiting restock or disposal.");
        gauge(out, "dev_device_returned_total", returnedDevices);
        metricHelp(out, "dev_device_disposed_total", "BusinessServer devices currently marked disposed.");
        gauge(out, "dev_device_disposed_total", disposedDevices);
        metricHelp(out, "dev_device_offline_total", "Bound devices with no recent last_seen_at.");
        gauge(out, "dev_device_offline_total", offlineDevices);
        metricHelp(out, "u8_ota_install_total", "Firmware rollout install count across active, published, and paused releases.");
        gauge(out, "u8_ota_install_total", otaInstallCount);
        metricHelp(out, "u8_ota_failed_total", "Firmware rollout failure count across active, published, and paused releases.");
        gauge(out, "u8_ota_failed_total", otaFailureCount);
        metricHelp(out, "u8_ota_failure_ratio", "Firmware rollout failure ratio across active, published, and paused releases.");
        gauge(out, "u8_ota_failure_ratio", otaFailureRatio);
        metricHelp(out, "product_notification_pending_total", "Pending local product notification outbox rows.");
        gauge(out, "product_notification_pending_total", notificationPending);
        metricHelp(out, "product_notification_failed_total", "Failed local product notification outbox rows.");
        gauge(out, "product_notification_failed_total", notificationFailed);
        return out.toString();
    }

    private long countTasks(String status, Date since) {
        return v2TaskDao.selectCount(new LambdaQueryWrapper<V2TaskEntity>()
                .eq(V2TaskEntity::getStatus, status)
                .ge(V2TaskEntity::getUpdatedAt, since));
    }

    private long countDevicesByStatus(String status) {
        return v2DeviceDao.selectCount(new LambdaQueryWrapper<V2DeviceEntity>()
                .eq(V2DeviceEntity::getStatus, status));
    }

    private long countOfflineDevices(Date offlineCutoff) {
        return v2DeviceDao.selectCount(new LambdaQueryWrapper<V2DeviceEntity>()
                .eq(V2DeviceEntity::getStatus, "bound")
                .and(wrapper -> wrapper.isNull(V2DeviceEntity::getLastSeenAt)
                        .or()
                        .lt(V2DeviceEntity::getLastSeenAt, offlineCutoff)));
    }

    private long sumFirmwareInteger(String column) {
        return v2FirmwareReleaseDao.selectList(new LambdaQueryWrapper<V2FirmwareReleaseEntity>()
                .in(V2FirmwareReleaseEntity::getStatus, RELEASE_STATUS_ACTIVE, RELEASE_STATUS_PUBLISHED, RELEASE_STATUS_PAUSED))
                .stream()
                .mapToLong(release -> "failure_count".equals(column)
                        ? nullToZero(release.getFailureCount())
                        : nullToZero(release.getInstallCount()))
                .sum();
    }

    private long countNotificationEvents(String status) {
        return v2ProductNotificationEventDao.selectCount(new LambdaQueryWrapper<V2ProductNotificationEventEntity>()
                .eq(V2ProductNotificationEventEntity::getStatus, status));
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static void metricHelp(StringBuilder out, String name, String help) {
        out.append("# HELP ").append(name).append(' ').append(help).append('\n');
        out.append("# TYPE ").append(name).append(" gauge\n");
    }

    private static void gauge(StringBuilder out, String name, long value) {
        out.append(name).append(' ').append(value).append('\n');
    }

    private static void gauge(StringBuilder out, String name, double value) {
        out.append(name).append(' ').append(String.format(java.util.Locale.ROOT, "%.6f", value)).append('\n');
    }
}
