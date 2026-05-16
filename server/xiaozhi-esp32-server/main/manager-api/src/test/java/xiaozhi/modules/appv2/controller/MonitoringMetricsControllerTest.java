package xiaozhi.modules.appv2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.modules.appv2.service.MonitoringMetricsService;

@ExtendWith(MockitoExtension.class)
class MonitoringMetricsControllerTest {
    @Mock
    private MonitoringMetricsService monitoringMetricsService;

    @Test
    void metricsReturnsPrometheusText() {
        when(monitoringMetricsService.prometheusText()).thenReturn("biz_task_failure_ratio 0.000000\n");
        MonitoringMetricsController controller = new MonitoringMetricsController(monitoringMetricsService);

        assertEquals("biz_task_failure_ratio 0.000000\n", controller.metrics());
    }
}
