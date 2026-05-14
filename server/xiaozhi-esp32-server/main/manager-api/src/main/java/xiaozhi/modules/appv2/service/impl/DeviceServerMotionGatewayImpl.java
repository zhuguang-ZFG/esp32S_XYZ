package xiaozhi.modules.appv2.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;

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
        body.put("account_id", task.getAccountId());
        body.put("capability", task.getCapability());
        body.put("source", task.getSource());
        body.put("request_id", task.getRequestId());
        body.put("trace_id", task.getTraceId());
        body.put("params", request.getParams());
        body.put("constraints", request.getConstraints());

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
}
