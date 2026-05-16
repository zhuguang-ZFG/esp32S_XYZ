package xiaozhi.modules.appv2.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.modules.appv2.dao.V2DeviceDao;
import xiaozhi.modules.appv2.dao.V2FirmwareReleaseDao;
import xiaozhi.modules.appv2.dao.V2ProductNotificationEventDao;
import xiaozhi.modules.appv2.dao.V2TaskDao;
import xiaozhi.modules.appv2.entity.V2FirmwareReleaseEntity;
import xiaozhi.modules.appv2.service.impl.MonitoringMetricsServiceImpl;

@ExtendWith(MockitoExtension.class)
class MonitoringMetricsServiceImplTest {
    @Mock
    private V2TaskDao v2TaskDao;
    @Mock
    private V2DeviceDao v2DeviceDao;
    @Mock
    private V2FirmwareReleaseDao v2FirmwareReleaseDao;
    @Mock
    private V2ProductNotificationEventDao v2ProductNotificationEventDao;

    @Test
    void prometheusTextExposesTaskOfflineAndOtaMetrics() {
        when(v2TaskDao.selectCount(any())).thenReturn(95L, 5L);
        when(v2DeviceDao.selectCount(any())).thenReturn(100L, 2L, 4L, 1L, 3L);
        V2FirmwareReleaseEntity release = new V2FirmwareReleaseEntity();
        release.setInstallCount(100);
        release.setFailureCount(4);
        when(v2FirmwareReleaseDao.selectList(any())).thenReturn(List.of(release), List.of(release));
        when(v2ProductNotificationEventDao.selectCount(any())).thenReturn(6L, 1L);
        MonitoringMetricsServiceImpl service =
                new MonitoringMetricsServiceImpl(v2TaskDao, v2DeviceDao, v2FirmwareReleaseDao, v2ProductNotificationEventDao);

        String text = service.prometheusText();

        assertTrue(text.contains("biz_task_done_total 95"));
        assertTrue(text.contains("biz_task_failed_total 5"));
        assertTrue(text.contains("biz_task_failure_ratio 0.050000"));
        assertTrue(text.contains("dev_device_bound_total 100"));
        assertTrue(text.contains("dev_device_rma_in_progress_total 2"));
        assertTrue(text.contains("dev_device_returned_total 4"));
        assertTrue(text.contains("dev_device_disposed_total 1"));
        assertTrue(text.contains("dev_device_offline_total 3"));
        assertTrue(text.contains("Firmware rollout install count across active, published, and paused releases."));
        assertTrue(text.contains("Firmware rollout failure count across active, published, and paused releases."));
        assertTrue(text.contains("Firmware rollout failure ratio across active, published, and paused releases."));
        assertTrue(text.contains("u8_ota_install_total 100"));
        assertTrue(text.contains("u8_ota_failed_total 4"));
        assertTrue(text.contains("u8_ota_failure_ratio 0.040000"));
        assertTrue(text.contains("product_notification_pending_total 6"));
        assertTrue(text.contains("product_notification_failed_total 1"));
    }

    @Test
    void prometheusTextTreatsMissingFirmwareCountersAsZero() {
        when(v2TaskDao.selectCount(any())).thenReturn(0L, 0L);
        when(v2DeviceDao.selectCount(any())).thenReturn(0L, 0L, 0L, 0L, 0L);
        when(v2FirmwareReleaseDao.selectList(any()))
                .thenReturn(List.of(new V2FirmwareReleaseEntity()), List.of(new V2FirmwareReleaseEntity()));
        when(v2ProductNotificationEventDao.selectCount(any())).thenReturn(0L, 0L);
        MonitoringMetricsServiceImpl service =
                new MonitoringMetricsServiceImpl(v2TaskDao, v2DeviceDao, v2FirmwareReleaseDao, v2ProductNotificationEventDao);

        String text = service.prometheusText();

        assertTrue(text.contains("u8_ota_install_total 0"));
        assertTrue(text.contains("u8_ota_failed_total 0"));
        assertTrue(text.contains("u8_ota_failure_ratio 0.000000"));
        assertTrue(text.contains("biz_task_failure_ratio 0.000000"));
    }
}
