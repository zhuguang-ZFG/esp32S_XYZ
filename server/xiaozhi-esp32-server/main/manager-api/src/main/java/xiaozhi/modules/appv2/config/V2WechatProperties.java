package xiaozhi.modules.appv2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * v2 微信小程序登录配置。
 *
 * 安全约定（v2 §11.1）：登录链路必须由服务端调微信
 * jscode2session 接口用临时 code 换取 unionid/openid，禁止信任客户端直传。
 *
 * - mock-mode=true 时跳过真实 HTTP 调用，仅用于本地/CI；生产必须设为 false
 * - app-id / app-secret 缺失时 gateway 直接抛错，避免静默放行
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "v2.wechat.miniapp")
public class V2WechatProperties {
    private String appId;
    private String appSecret;
    private String jscode2sessionUrl = "https://api.weixin.qq.com/sns/jscode2session";
    private boolean mockMode = false;
}
