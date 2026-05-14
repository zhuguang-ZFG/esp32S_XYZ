package xiaozhi.modules.appv2.controller;

import java.util.Map;

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
import xiaozhi.modules.appv2.service.AppV2Service;

/**
 * DeviceServer（xiaozhi-server）经 HTTP 上行的 motion_event 入口（M2.6）。
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

    @PostMapping("/motion_event")
    public Result<Void> motionEvent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        String expected = StringUtils.trimToEmpty(deviceServerProperties.getInternalToken());
        if (StringUtils.isBlank(expected)) {
            log.debug("motion_event ingest 已禁用（v2.device-server.internal-token 未配置）");
            return new Result<Void>().error(ErrorCode.INTERNAL_SERVER_ERROR, "motion_event ingest disabled");
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return new Result<Void>().error(ErrorCode.UNAUTHORIZED, "missing bearer token");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (!expected.equals(token)) {
            return new Result<Void>().error(ErrorCode.UNAUTHORIZED, "invalid token");
        }
        appV2Service.ingestMotionEvent(body);
        return new Result<Void>().ok(null);
    }
}
