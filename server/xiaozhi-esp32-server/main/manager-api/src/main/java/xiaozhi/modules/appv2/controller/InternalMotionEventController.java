package xiaozhi.modules.appv2.controller;

import java.util.Map;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.appv2.config.V2DeviceServerProperties;
import xiaozhi.modules.appv2.dao.V2DeviceDao;
import xiaozhi.modules.appv2.dto.V2SubmitTaskRequest;
import xiaozhi.modules.appv2.dto.V2SubmitTaskResponse;
import xiaozhi.modules.appv2.dto.V2FirmwareReleaseResponse;
import xiaozhi.modules.appv2.dto.V2VoiceprintCacheEntry;
import xiaozhi.modules.appv2.entity.V2DeviceEntity;
import xiaozhi.modules.appv2.service.AppV2Service;
import xiaozhi.modules.appv2.service.VoiceprintEnrollmentService;
import xiaozhi.modules.appv2.service.firmware.FirmwareReleaseService;

/**
 * DeviceServer（xiaozhi-server）经 HTTP 上行的 motion_event / device_info 入口（M2.6 / M2.13）。
 * <p>
 * 鉴权与 {@code POST /internal/v1/motion_task} 一致：{@code Authorization: Bearer} 须等于
 * {@code v2.device-server.internal-token}。
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/internal/v1")
public class InternalMotionEventController {

    private final V2DeviceServerProperties deviceServerProperties;
    private final AppV2Service appV2Service;
    private final VoiceprintEnrollmentService voiceprintEnrollmentService;
    private final FirmwareReleaseService firmwareReleaseService;
    private final V2DeviceDao v2DeviceDao;

    @PostMapping("/motion_event")
    public Result<Void> motionEvent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Result<Void> auth = authorizeInternalRequest(authorization, "motion_event ingest");
        if (auth != null) {
            return auth;
        }
        Result<Void> deviceAllowed = requireDeviceNotDisposed(body);
        if (deviceAllowed != null) {
            return deviceAllowed;
        }
        appV2Service.ingestMotionEvent(body);
        return new Result<Void>().ok(null);
    }

    @PostMapping("/device_info")
    public Result<Void> deviceInfo(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Result<Void> auth = authorizeInternalRequest(authorization, "device_info ingest");
        if (auth != null) {
            return auth;
        }
        Result<Void> deviceAllowed = requireDeviceNotDisposed(body);
        if (deviceAllowed != null) {
            return deviceAllowed;
        }
        appV2Service.ingestDeviceInfo(body);
        return new Result<Void>().ok(null);
    }

    @PostMapping("/self_check")
    public Result<Void> selfCheck(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Result<Void> auth = authorizeInternalRequest(authorization, "self_check ingest");
        if (auth != null) {
            return auth;
        }
        Result<Void> deviceAllowed = requireDeviceNotDisposed(body);
        if (deviceAllowed != null) {
            return deviceAllowed;
        }
        appV2Service.ingestSelfCheck(body);
        return new Result<Void>().ok(null);
    }

    @PostMapping("/voice_task")
    public Result<V2SubmitTaskResponse> voiceTask(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Result<Void> auth = authorizeInternalRequest(authorization, "voice_task submit");
        if (auth != null) {
            return new Result<V2SubmitTaskResponse>().error(auth.getCode(), auth.getMsg());
        }
        Result<Void> deviceAllowed = requireDeviceNotDisposed(body);
        if (deviceAllowed != null) {
            return new Result<V2SubmitTaskResponse>().error(deviceAllowed.getCode(), deviceAllowed.getMsg());
        }
        String deviceId = stringValue(body.get("device_id"));
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability(stringValue(body.get("capability")));
        request.setRequestId(stringValue(body.get("request_id")));
        request.setTraceId(stringValue(body.get("trace_id")));
        request.setSource(StringUtils.defaultIfBlank(stringValue(body.get("source")), "voice"));
        Object params = body.get("params");
        if (params instanceof Map<?, ?> paramsMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) paramsMap;
            request.setParams(cast);
        }
        Object constraints = body.get("constraints");
        if (constraints instanceof Map<?, ?> constraintsMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) constraintsMap;
            request.setConstraints(cast);
        }
        return new Result<V2SubmitTaskResponse>().ok(appV2Service.submitVoiceTask(deviceId, request));
    }

    @PostMapping("/voiceprints/cache")
    public Result<List<V2VoiceprintCacheEntry>> voiceprintCache(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Result<Void> auth = authorizeInternalRequest(authorization, "voiceprint cache");
        if (auth != null) {
            return new Result<List<V2VoiceprintCacheEntry>>().error(auth.getCode(), auth.getMsg());
        }
        Result<Void> deviceAllowed = requireDeviceNotDisposed(body);
        if (deviceAllowed != null) {
            return new Result<List<V2VoiceprintCacheEntry>>().error(deviceAllowed.getCode(), deviceAllowed.getMsg());
        }
        String deviceId = stringValue(body.get("device_id"));
        return new Result<List<V2VoiceprintCacheEntry>>().ok(voiceprintEnrollmentService.activeCacheForDevice(deviceId));
    }

    @PostMapping("/firmware/upgrade-plan")
    public Result<V2FirmwareReleaseResponse> firmwareUpgradePlan(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Result<Void> auth = authorizeInternalRequest(authorization, "firmware upgrade plan");
        if (auth != null) {
            return new Result<V2FirmwareReleaseResponse>().error(auth.getCode(), auth.getMsg());
        }
        Result<Void> deviceAllowed = requireDeviceNotDisposed(body);
        if (deviceAllowed != null) {
            return new Result<V2FirmwareReleaseResponse>().error(deviceAllowed.getCode(), deviceAllowed.getMsg());
        }
        String deviceId = stringValue(body.get("device_id"));
        String channel = StringUtils.defaultIfBlank(stringValue(body.get("channel")), FirmwareReleaseService.CHANNEL_DEV);
        String currentVersion = stringValue(body.get("current_version"));
        return new Result<V2FirmwareReleaseResponse>().ok(firmwareReleaseService
                .findUpgradeForDevice(channel, deviceId, currentVersion)
                .map(V2FirmwareReleaseResponse::fromEntity)
                .orElse(null));
    }

    @PostMapping("/firmware/install-result")
    public Result<V2FirmwareReleaseResponse> firmwareInstallResult(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        Result<Void> auth = authorizeInternalRequest(authorization, "firmware install result");
        if (auth != null) {
            return new Result<V2FirmwareReleaseResponse>().error(auth.getCode(), auth.getMsg());
        }
        Result<Void> deviceAllowed = requireDeviceNotDisposed(body);
        if (deviceAllowed != null) {
            return new Result<V2FirmwareReleaseResponse>().error(deviceAllowed.getCode(), deviceAllowed.getMsg());
        }
        String releaseId = StringUtils.defaultIfBlank(stringValue(body.get("release_id")), stringValue(body.get("releaseId")));
        if (StringUtils.isBlank(releaseId)) {
            return new Result<V2FirmwareReleaseResponse>().error(ErrorCode.PARAMS_GET_ERROR, "missing release_id");
        }
        return new Result<V2FirmwareReleaseResponse>().ok(V2FirmwareReleaseResponse.fromEntity(
                firmwareReleaseService.recordInstallResult(releaseId, Boolean.TRUE.equals(body.get("success")))));
    }

    private Result<Void> requireDeviceNotDisposed(Map<String, Object> body) {
        String deviceId = stringValue(body.get("device_id"));
        if (deviceId == null) {
            return new Result<Void>().error(ErrorCode.PARAMS_GET_ERROR, "missing device_id");
        }
        V2DeviceEntity device = v2DeviceDao.selectById(deviceId);
        if (device != null && "disposed".equals(device.getStatus())) {
            return new Result<Void>().error(ErrorCode.FORBIDDEN, "E_DEVICE_DISPOSED: device is disposed");
        }
        return null;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        return StringUtils.trimToNull(String.valueOf(value));
    }

    private Result<Void> authorizeInternalRequest(String authorization, String label) {
        String expected = StringUtils.trimToEmpty(deviceServerProperties.getInternalToken());
        if (StringUtils.isBlank(expected)) {
            log.debug("{} 已禁用（v2.device-server.internal-token 未配置）", label);
            return new Result<Void>().error(ErrorCode.INTERNAL_SERVER_ERROR, label + " disabled");
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return new Result<Void>().error(ErrorCode.UNAUTHORIZED, "missing bearer token");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (!expected.equals(token)) {
            return new Result<Void>().error(ErrorCode.UNAUTHORIZED, "invalid token");
        }
        return null;
    }
}
