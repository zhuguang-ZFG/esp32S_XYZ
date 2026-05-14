package xiaozhi.modules.appv2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * BusinessServer → DeviceServer（xiaozhi-server HTTP）内部调用配置（M2.3 Edge-B）。
 * <p>
 * base-url 与 internal-token 均非空时，提交任务成功落库后会转发 {@code POST /internal/v1/motion_task}。
 * 开发/CI 未接 DeviceServer 时可留空以跳过转发。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "v2.device-server")
public class V2DeviceServerProperties {
    /**
     * DeviceServer 根地址，例如 http://127.0.0.1:8003（不含尾斜杠）。
     */
    private String baseUrl = "";
    /**
     * 与 xiaozhi-server {@code server.internal_motion_task_token} 一致的共享密钥。
     */
    private String internalToken = "";
}
