package xiaozhi.modules.appv2.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import xiaozhi.modules.appv2.service.MonitoringMetricsService;

@AllArgsConstructor
@RestController
@RequestMapping("/internal/v1")
public class MonitoringMetricsController {
    private final MonitoringMetricsService monitoringMetricsService;

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics() {
        return monitoringMetricsService.prometheusText();
    }
}
