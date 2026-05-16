package xiaozhi.modules.appv2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import cn.hutool.json.JSONUtil;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.appv2.config.V2DeviceServerProperties;
import xiaozhi.modules.appv2.dto.V2SubmitTaskRequest;
import xiaozhi.modules.appv2.entity.V2TaskEntity;
import xiaozhi.modules.appv2.service.impl.DeviceServerMotionGatewayImpl;

class DeviceServerMotionGatewayImplTest {

    @Test
    void skipsForwardWhenGatewayConfigMissing() {
        V2DeviceServerProperties properties = new V2DeviceServerProperties();
        RestTemplate restTemplate = org.mockito.Mockito.mock(RestTemplate.class);
        DeviceServerMotionGatewayImpl gateway = new DeviceServerMotionGatewayImpl(properties, restTemplate);

        gateway.forwardAcceptedTask("dev-1", task(), request());

        verify(restTemplate, never()).postForEntity(any(String.class), any(), eq(String.class));
    }

    @Test
    void postsAcceptedTaskToDeviceServer() {
        V2DeviceServerProperties properties = new V2DeviceServerProperties();
        properties.setBaseUrl("http://device-server/api");
        properties.setInternalToken("secret-token");
        RestTemplate restTemplate = org.mockito.Mockito.mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));
        DeviceServerMotionGatewayImpl gateway = new DeviceServerMotionGatewayImpl(properties, restTemplate);

        gateway.forwardAcceptedTask("dev-1", task(), request());

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<HttpEntity<String>> entityCaptor =
                org.mockito.ArgumentCaptor.forClass((Class) HttpEntity.class);
        org.mockito.ArgumentCaptor<String> urlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(restTemplate).postForEntity(urlCaptor.capture(), entityCaptor.capture(), eq(String.class));
        assertEquals("http://device-server/api/internal/v1/motion_task", urlCaptor.getValue());

        HttpHeaders headers = entityCaptor.getValue().getHeaders();
        assertEquals("Bearer secret-token", headers.getFirst(HttpHeaders.AUTHORIZATION));

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> payload = JSONUtil.toBean(entityCaptor.getValue().getBody(), java.util.Map.class);
        assertEquals("task-1", payload.get("task_id"));
        assertEquals("dev-1", payload.get("device_id"));
        assertEquals("req-1", payload.get("request_id"));
        assertEquals("trace-1", payload.get("trace_id"));
        assertEquals("home", payload.get("capability"));
    }

    @Test
    void usesDispatchCapabilityWhenTaskWasProjected() {
        V2DeviceServerProperties properties = new V2DeviceServerProperties();
        properties.setBaseUrl("http://device-server");
        properties.setInternalToken("secret-token");
        RestTemplate restTemplate = org.mockito.Mockito.mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));
        DeviceServerMotionGatewayImpl gateway = new DeviceServerMotionGatewayImpl(properties, restTemplate);

        V2TaskEntity task = task();
        task.setCapability("write_text");
        V2SubmitTaskRequest dispatch = request();
        dispatch.setCapability("run_path");
        dispatch.setParams(java.util.Map.of("feed", 900, "path", java.util.List.of(java.util.Map.of("cmd", "M"))));

        gateway.forwardAcceptedTask("dev-1", task, dispatch);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<HttpEntity<String>> entityCaptor =
                org.mockito.ArgumentCaptor.forClass((Class) HttpEntity.class);
        verify(restTemplate).postForEntity(any(String.class), entityCaptor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> payload = JSONUtil.toBean(entityCaptor.getValue().getBody(), java.util.Map.class);
        assertEquals("run_path", payload.get("capability"));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> params = (java.util.Map<String, Object>) payload.get("params");
        assertEquals(900, params.get("feed"));
    }

    @Test
    void postsRuntimeStatusRefreshAsGetStatusWithoutTaskPersistenceFields() {
        V2DeviceServerProperties properties = new V2DeviceServerProperties();
        properties.setBaseUrl("http://device-server");
        properties.setInternalToken("secret-token");
        RestTemplate restTemplate = org.mockito.Mockito.mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));
        DeviceServerMotionGatewayImpl gateway = new DeviceServerMotionGatewayImpl(properties, restTemplate);

        gateway.forwardRuntimeStatusRefresh("dev-1", 42L, "runtime_stale", "trace-refresh-1");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<HttpEntity<String>> entityCaptor =
                org.mockito.ArgumentCaptor.forClass((Class) HttpEntity.class);
        verify(restTemplate).postForEntity(any(String.class), entityCaptor.capture(), eq(String.class));

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> payload = JSONUtil.toBean(entityCaptor.getValue().getBody(), java.util.Map.class);
        assertEquals("dev-1", payload.get("device_id"));
        assertEquals(42, ((Number) payload.get("account_id")).intValue());
        assertEquals("get_status", payload.get("capability"));
        assertEquals("trace-refresh-1", payload.get("trace_id"));
        assertEquals(false, payload.containsKey("source"));
        assertEquals(true, String.valueOf(payload.get("task_id")).startsWith("runtime-refresh-"));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> params = (java.util.Map<String, Object>) payload.get("params");
        assertEquals("runtime_stale", params.get("reason"));
    }

    @Test
    void postsVoiceprintCacheClearWithoutThrowingOnGatewayFailure() {
        V2DeviceServerProperties properties = new V2DeviceServerProperties();
        properties.setBaseUrl("http://device-server");
        properties.setInternalToken("secret-token");
        RestTemplate restTemplate = org.mockito.Mockito.mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));
        DeviceServerMotionGatewayImpl gateway = new DeviceServerMotionGatewayImpl(properties, restTemplate);

        gateway.clearVoiceprintCache("dev-1", "device_transfer");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<HttpEntity<String>> entityCaptor =
                org.mockito.ArgumentCaptor.forClass((Class) HttpEntity.class);
        org.mockito.ArgumentCaptor<String> urlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(restTemplate).postForEntity(urlCaptor.capture(), entityCaptor.capture(), eq(String.class));
        assertEquals("http://device-server/internal/v1/voiceprints/cache/clear", urlCaptor.getValue());
        assertEquals("Bearer secret-token", entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> payload = JSONUtil.toBean(entityCaptor.getValue().getBody(), java.util.Map.class);
        assertEquals("dev-1", payload.get("device_id"));
        assertEquals("device_transfer", payload.get("reason"));
    }

    @Test
    void wrapsTransportFailuresAsRenException() {
        V2DeviceServerProperties properties = new V2DeviceServerProperties();
        properties.setBaseUrl("http://device-server");
        properties.setInternalToken("secret-token");
        RestTemplate restTemplate = org.mockito.Mockito.mock(RestTemplate.class);
        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenThrow(new RestClientException("timeout"));
        DeviceServerMotionGatewayImpl gateway = new DeviceServerMotionGatewayImpl(properties, restTemplate);

        RenException error = assertThrows(RenException.class,
                () -> gateway.forwardAcceptedTask("dev-1", task(), request()));

        assertEquals(true, error.getMessage().contains("timeout"));
    }

    private static V2TaskEntity task() {
        V2TaskEntity task = new V2TaskEntity();
        task.setId("task-1");
        task.setAccountId(42L);
        task.setCapability("home");
        task.setSource("client");
        task.setRequestId("req-1");
        task.setTraceId("trace-1");
        return task;
    }

    private static V2SubmitTaskRequest request() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("home");
        request.setParams(java.util.Map.of("angle", 90));
        request.setConstraints(java.util.Map.of("speed", "slow"));
        return request;
    }
}
