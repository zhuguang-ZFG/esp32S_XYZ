package xiaozhi.modules.appv2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.appv2.config.V2DeviceServerProperties;
import xiaozhi.modules.appv2.dao.V2DeviceDao;
import xiaozhi.modules.appv2.dto.V2SubmitTaskRequest;
import xiaozhi.modules.appv2.dto.V2SubmitTaskResponse;
import xiaozhi.modules.appv2.dto.V2VoiceprintCacheEntry;
import xiaozhi.modules.appv2.entity.V2DeviceEntity;
import xiaozhi.modules.appv2.entity.V2FirmwareReleaseEntity;
import xiaozhi.modules.appv2.service.AppV2Service;
import xiaozhi.modules.appv2.service.VoiceprintEnrollmentService;
import xiaozhi.modules.appv2.service.firmware.FirmwareReleaseService;

@ExtendWith(MockitoExtension.class)
class InternalMotionEventControllerTest {

    @Mock
    private V2DeviceServerProperties properties;
    @Mock
    private AppV2Service appV2Service;
    @Mock
    private VoiceprintEnrollmentService voiceprintEnrollmentService;
    @Mock
    private FirmwareReleaseService firmwareReleaseService;
    @Mock
    private V2DeviceDao v2DeviceDao;

    @Test
    void rejectsWhenInternalTokenIsNotConfigured() {
        when(properties.getInternalToken()).thenReturn(" ");
        InternalMotionEventController controller = newController();

        Result<Void> response = controller.motionEvent("Bearer secret-token", payload());

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, response.getCode());
        assertEquals("motion_event ingest disabled", response.getMsg());
    }

    @Test
    void deviceInfoRejectsWhenInternalTokenIsNotConfigured() {
        when(properties.getInternalToken()).thenReturn(" ");
        InternalMotionEventController controller = newController();

        Result<Void> response = controller.deviceInfo("Bearer secret-token", deviceInfoPayload());

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, response.getCode());
        assertEquals("device_info ingest disabled", response.getMsg());
    }

    @Test
    void rejectsMissingBearerToken() {
        when(properties.getInternalToken()).thenReturn("secret-token");
        InternalMotionEventController controller = newController();

        Result<Void> response = controller.motionEvent(null, payload());

        assertEquals(ErrorCode.UNAUTHORIZED, response.getCode());
        assertEquals("missing bearer token", response.getMsg());
    }

    @Test
    void rejectsWrongBearerToken() {
        when(properties.getInternalToken()).thenReturn("secret-token");
        InternalMotionEventController controller = newController();

        Result<Void> response = controller.motionEvent("Bearer wrong-token", payload());

        assertEquals(ErrorCode.UNAUTHORIZED, response.getCode());
        assertEquals("invalid token", response.getMsg());
    }

    @Test
    void forwardsMotionEventWhenTokenMatches() {
        when(properties.getInternalToken()).thenReturn("secret-token");
        InternalMotionEventController controller = newController();
        Map<String, Object> payload = payload();
        when(v2DeviceDao.selectById("dev-1")).thenReturn(device("bound"));

        Result<Void> response = controller.motionEvent("Bearer secret-token", payload);

        assertEquals(0, response.getCode());
        assertEquals("success", response.getMsg());
        assertNull(response.getData());
        verify(appV2Service).ingestMotionEvent(payload);
    }

    @Test
    void forwardsDeviceInfoWhenTokenMatches() {
        when(properties.getInternalToken()).thenReturn("secret-token");
        InternalMotionEventController controller = newController();
        Map<String, Object> payload = deviceInfoPayload();
        when(v2DeviceDao.selectById("dev-1")).thenReturn(device("bound"));

        Result<Void> response = controller.deviceInfo("Bearer secret-token", payload);

        assertEquals(0, response.getCode());
        assertEquals("success", response.getMsg());
        assertNull(response.getData());
        verify(appV2Service).ingestDeviceInfo(payload);
    }

    @Test
    void forwardsSelfCheckWhenTokenMatches() {
        when(properties.getInternalToken()).thenReturn("secret-token");
        InternalMotionEventController controller = newController();
        Map<String, Object> payload = selfCheckPayload();
        when(v2DeviceDao.selectById("dev-1")).thenReturn(device("bound"));

        Result<Void> response = controller.selfCheck("Bearer secret-token", payload);

        assertEquals(0, response.getCode());
        assertEquals("success", response.getMsg());
        assertNull(response.getData());
        verify(appV2Service).ingestSelfCheck(payload);
    }

    @Test
    void selfCheckRejectsWhenInternalTokenIsNotConfigured() {
        when(properties.getInternalToken()).thenReturn(" ");
        InternalMotionEventController controller = newController();

        Result<Void> response = controller.selfCheck("Bearer secret-token", selfCheckPayload());

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, response.getCode());
        assertEquals("self_check ingest disabled", response.getMsg());
    }

    @Test
    void forwardsVoiceTaskWhenTokenMatches() {
        when(properties.getInternalToken()).thenReturn("secret-token");
        InternalMotionEventController controller = newController();
        Map<String, Object> payload = voiceTaskPayload();
        when(v2DeviceDao.selectById("dev-voice-1")).thenReturn(device("bound"));
        when(appV2Service.submitVoiceTask(org.mockito.ArgumentMatchers.eq("dev-voice-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new V2SubmitTaskResponse("task-voice-1", "accepted"));

        Result<V2SubmitTaskResponse> response = controller.voiceTask("Bearer secret-token", payload);

        assertEquals(0, response.getCode());
        assertEquals("task-voice-1", response.getData().getTaskId());
        org.mockito.ArgumentCaptor<V2SubmitTaskRequest> captor =
                org.mockito.ArgumentCaptor.forClass(V2SubmitTaskRequest.class);
        verify(appV2Service).submitVoiceTask(org.mockito.ArgumentMatchers.eq("dev-voice-1"), captor.capture());
        V2SubmitTaskRequest request = captor.getValue();
        assertEquals("write_text", request.getCapability());
        assertEquals("voice-req-1", request.getRequestId());
        assertEquals("voice", request.getSource());
        @SuppressWarnings("unchecked")
        Map<String, Object> voiceprint = (Map<String, Object>) request.getConstraints().get("voiceprint");
        assertEquals(1, voiceprint.get("member_id"));
        assertEquals("owner", voiceprint.get("member_type"));
        assertEquals("local:parent", voiceprint.get("speaker_ref"));
        assertEquals("你好", request.getParams().get("text"));
    }

    @Test
    void forwardsVoiceprintCacheWhenTokenMatches() {
        when(properties.getInternalToken()).thenReturn("secret-token");
        InternalMotionEventController controller = newController();
        when(v2DeviceDao.selectById("dev-voice-1")).thenReturn(device("bound"));
        when(voiceprintEnrollmentService.activeCacheForDevice("dev-voice-1"))
                .thenReturn(List.of(new V2VoiceprintCacheEntry(1L, "Parent", "owner", "local:parent", "a".repeat(64), "active", null)));

        Result<List<V2VoiceprintCacheEntry>> response =
                controller.voiceprintCache("Bearer secret-token", Map.of("device_id", "dev-voice-1"));

        assertEquals(0, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("local:parent", response.getData().get(0).getSpeakerRef());
        verify(voiceprintEnrollmentService).activeCacheForDevice("dev-voice-1");
    }

    @Test
    void forwardsFirmwareUpgradePlanWhenTokenMatches() {
        when(properties.getInternalToken()).thenReturn("secret-token");
        InternalMotionEventController controller = newController();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("device_id", "dev-ota-1");
        payload.put("channel", "dev");
        payload.put("current_version", "1.0.0");
        when(v2DeviceDao.selectById("dev-ota-1")).thenReturn(device("bound"));
        when(firmwareReleaseService.findUpgradeForDevice("dev", "dev-ota-1", "1.0.0"))
                .thenReturn(Optional.of(firmwareRelease()));

        Result<xiaozhi.modules.appv2.dto.V2FirmwareReleaseResponse> response =
                controller.firmwareUpgradePlan("Bearer secret-token", payload);

        assertEquals(0, response.getCode());
        assertEquals("1.2.3", response.getData().getVersion());
        assertEquals("https://ota.example.com/u8.bin", response.getData().getUrl());
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", response.getData().getSha256());
        assertEquals("c2ln", response.getData().getSignature());
        verify(firmwareReleaseService).findUpgradeForDevice("dev", "dev-ota-1", "1.0.0");
    }

    @Test
    void forwardsFirmwareInstallResultWhenTokenMatches() {
        when(properties.getInternalToken()).thenReturn("secret-token");
        InternalMotionEventController controller = newController();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("device_id", "dev-ota-1");
        payload.put("release_id", "rel-1");
        payload.put("success", false);
        when(v2DeviceDao.selectById("dev-ota-1")).thenReturn(device("bound"));
        V2FirmwareReleaseEntity paused = firmwareRelease();
        paused.setStatus("paused");
        paused.setInstallCount(1);
        paused.setFailureCount(1);
        when(firmwareReleaseService.recordInstallResult("rel-1", false)).thenReturn(paused);

        Result<xiaozhi.modules.appv2.dto.V2FirmwareReleaseResponse> response =
                controller.firmwareInstallResult("Bearer secret-token", payload);

        assertEquals(0, response.getCode());
        assertEquals("paused", response.getData().getStatus());
        assertEquals(1, response.getData().getFailureCount());
        verify(firmwareReleaseService).recordInstallResult("rel-1", false);
    }

    @Test
    void rejectsFirmwareInstallResultWithoutReleaseId() {
        when(properties.getInternalToken()).thenReturn("secret-token");
        InternalMotionEventController controller = newController();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("device_id", "dev-ota-1");
        payload.put("success", true);
        when(v2DeviceDao.selectById("dev-ota-1")).thenReturn(device("bound"));

        Result<xiaozhi.modules.appv2.dto.V2FirmwareReleaseResponse> response =
                controller.firmwareInstallResult("Bearer secret-token", payload);

        assertEquals(ErrorCode.PARAMS_GET_ERROR, response.getCode());
        assertEquals("missing release_id", response.getMsg());
    }

    @Test
    void rejectsDisposedDeviceBeforeForwardingRuntimeEvents() {
        when(properties.getInternalToken()).thenReturn("secret-token");
        when(v2DeviceDao.selectById("dev-1")).thenReturn(device("disposed"));
        InternalMotionEventController controller = newController();

        Result<Void> response = controller.motionEvent("Bearer secret-token", payload());

        assertEquals(ErrorCode.FORBIDDEN, response.getCode());
        assertEquals("E_DEVICE_DISPOSED: device is disposed", response.getMsg());
    }

    @Test
    void rejectsDisposedDeviceBeforeSubmittingVoiceTask() {
        when(properties.getInternalToken()).thenReturn("secret-token");
        when(v2DeviceDao.selectById("dev-voice-1")).thenReturn(device("disposed"));
        InternalMotionEventController controller = newController();

        Result<V2SubmitTaskResponse> response = controller.voiceTask("Bearer secret-token", voiceTaskPayload());

        assertEquals(ErrorCode.FORBIDDEN, response.getCode());
        assertEquals("E_DEVICE_DISPOSED: device is disposed", response.getMsg());
    }

    private InternalMotionEventController newController() {
        return new InternalMotionEventController(properties, appV2Service, voiceprintEnrollmentService, firmwareReleaseService, v2DeviceDao);
    }

    private static Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", "task-1");
        payload.put("device_id", "dev-1");
        payload.put("phase", "running");
        payload.put("capability", "home");
        return payload;
    }

    private static Map<String, Object> deviceInfoPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", "task-info");
        payload.put("device_id", "dev-1");
        payload.put("model", "DLC Motor Control P1 XYYZ");
        payload.put("hw_rev", "DLC_Motor_Control_P1_V1.0_260513");
        payload.put("fw_rev", "fake-u1");
        payload.put("workspace_mm", Map.of("x", 200.0, "y", 150.0, "z", 50.0));
        return payload;
    }

    private static Map<String, Object> selfCheckPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("device_id", "dev-1");
        payload.put("check_id", "startup");
        payload.put("scope", "startup");
        payload.put("status", "passed");
        payload.put("checks", Map.of(
                "nvs", Map.of("ok", true),
                "wifi", Map.of("ok", true),
                "u1_uart", Map.of("ok", true),
                "audio", Map.of("ok", true)));
        return payload;
    }

    private static Map<String, Object> voiceTaskPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("device_id", "dev-voice-1");
        payload.put("capability", "write_text");
        payload.put("source", "voice");
        payload.put("request_id", "voice-req-1");
        Map<String, Object> voiceprint = new LinkedHashMap<>();
        voiceprint.put("matched", true);
        voiceprint.put("member_id", 1);
        voiceprint.put("display_name", "Parent");
        voiceprint.put("member_type", "owner");
        voiceprint.put("speaker_ref", "local:parent");
        voiceprint.put("reason", "matched");
        payload.put("constraints", Map.of("voiceprint", voiceprint));
        payload.put("params", Map.of("text", "你好", "font_id", "kai_basic_v1"));
        return payload;
    }

    private static V2DeviceEntity device(String status) {
        V2DeviceEntity device = new V2DeviceEntity();
        device.setId("dev-1");
        device.setStatus(status);
        return device;
    }

    private static V2FirmwareReleaseEntity firmwareRelease() {
        V2FirmwareReleaseEntity release = new V2FirmwareReleaseEntity();
        release.setReleaseId("rel-1");
        release.setChannel("dev");
        release.setVersion("1.2.3");
        release.setUrl("https://ota.example.com/u8.bin");
        release.setSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        release.setSignature("c2ln");
        release.setRolloutPercent(100);
        release.setFailureThresholdPercent(20);
        release.setInstallCount(0);
        release.setFailureCount(0);
        release.setStatus("published");
        return release;
    }
}
