package xiaozhi.modules.appv2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * BusinessServer to DeviceServer (xiaozhi-server HTTP) internal call settings.
 * <p>
 * When both base-url and internal-token are configured, submitted tasks can be
 * forwarded to {@code POST /internal/v1/motion_task}. The same shared token is
 * also used by xiaozhi-server when it calls Manager API internal endpoints such
 * as {@code /internal/v1/motion_event}, {@code /internal/v1/device_info},
 * {@code /internal/v1/self_check}, voice-task, voiceprint-cache, and firmware
 * plan/result bridges. Local CI can leave it blank to skip DeviceServer
 * forwarding.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "v2.device-server")
public class V2DeviceServerProperties {
    /**
     * DeviceServer base URL, for example http://127.0.0.1:8003 without a trailing slash.
     */
    private String baseUrl = "";
    /**
     * Shared secret matching xiaozhi-server {@code server.internal_motion_task_token}.
     */
    private String internalToken = "";
}
