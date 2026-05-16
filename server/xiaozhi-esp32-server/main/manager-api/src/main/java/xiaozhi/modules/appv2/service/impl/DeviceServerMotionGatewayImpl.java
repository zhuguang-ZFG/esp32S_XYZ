package xiaozhi.modules.appv2.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import cn.hutool.json.JSONUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.appv2.config.V2DeviceServerProperties;
import xiaozhi.modules.appv2.dto.V2SubmitTaskRequest;
import xiaozhi.modules.appv2.entity.V2TaskEntity;
import xiaozhi.modules.appv2.service.DeviceServerMotionGateway;

@Slf4j
@Service
@AllArgsConstructor
public class DeviceServerMotionGatewayImpl implements DeviceServerMotionGateway {

    private final V2DeviceServerProperties deviceServerProperties;
    private final RestTemplate restTemplate;

    @Override
    public void forwardAcceptedTask(String deviceId, V2TaskEntity task, V2SubmitTaskRequest request) {
        postMotionTask(deviceId, task, request);
    }

    @Override
    public void forwardRuntimeStatusRefresh(String deviceId, Long accountId, String reason, String traceId) {
        V2TaskEntity task = new V2TaskEntity();
        task.setId("runtime-refresh-" + UUID.randomUUID());
        task.setAccountId(accountId);
        task.setCapability("get_status");

        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("get_status");
        request.setTraceId(traceId);
        request.setParams(Map.of("reason", StringUtils.defaultIfBlank(reason, "runtime_refresh")));

        postMotionTask(deviceId, task, request);
    }

    @Override
    public void clearVoiceprintCache(String deviceId, String reason) {
        String base = StringUtils.trimToEmpty(deviceServerProperties.getBaseUrl());
        String token = StringUtils.trimToEmpty(deviceServerProperties.getInternalToken());
        if (StringUtils.isBlank(base) || StringUtils.isBlank(token)) {
            log.debug("DeviceServer voiceprint cache clear skipped: base-url/internal-token missing");
            return;
        }
        String url = base.endsWith("/")
                ? base + "internal/v1/voiceprints/cache/clear"
                : base + "/internal/v1/voiceprints/cache/clear";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("device_id", deviceId);
        body.put("reason", StringUtils.defaultIfBlank(reason, "device_transfer"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        HttpEntity<String> entity = new HttpEntity<>(JSONUtil.toJsonStr(body), headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("DeviceServer voiceprint cache clear rejected: HTTP {}", response.getStatusCode().value());
            }
        } catch (RestClientException e) {
            log.warn("DeviceServer voiceprint cache clear failed: {}", e.getMessage());
        }
    }

    private void postMotionTask(String deviceId, V2TaskEntity task, V2SubmitTaskRequest request) {
        String base = StringUtils.trimToEmpty(deviceServerProperties.getBaseUrl());
        String token = StringUtils.trimToEmpty(deviceServerProperties.getInternalToken());
        if (StringUtils.isBlank(base) || StringUtils.isBlank(token)) {
            log.debug("DeviceServer motion_task 转发已跳过（v2.device-server.base-url 或 internal-token 未配置）");
            return;
        }
        String url = base.endsWith("/") ? base + "internal/v1/motion_task" : base + "/internal/v1/motion_task";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("task_id", task.getId());
        body.put("device_id", deviceId);
        body.put("capability", StringUtils.defaultIfBlank(request.getCapability(), task.getCapability()));
        putIfNotNull(body, "account_id", task.getAccountId());
        putIfNotBlank(body, "source", StringUtils.defaultIfBlank(request.getSource(), task.getSource()));
        putIfNotBlank(body, "request_id", StringUtils.defaultIfBlank(request.getRequestId(), task.getRequestId()));
        putIfNotBlank(body, "trace_id", StringUtils.defaultIfBlank(request.getTraceId(), task.getTraceId()));
        putIfNotNull(body, "params", request.getParams());
        putIfNotNull(body, "constraints", request.getConstraints());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        String json = JSONUtil.toJsonStr(body);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RenException("DeviceServer motion_task 拒绝: HTTP " + response.getStatusCode().value());
            }
        } catch (RestClientException e) {
            throw new RenException("DeviceServer motion_task 转发失败: " + e.getMessage(), e);
        }
    }

    private static void putIfNotBlank(Map<String, Object> body, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            body.put(key, value);
        }
    }

    private static void putIfNotNull(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }
}
