package xiaozhi.modules.appv2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "v2.dashscope")
public class V2DashScopeProperties {
    private boolean enabled = false;
    private String apiKey;
    private String model = "qwen-plus";
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private int timeoutSeconds = 10;
}
