package xiaozhi.modules.appv2.service.impl;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.appv2.config.V2WechatProperties;
import xiaozhi.modules.appv2.service.WechatLoginGateway;

/**
 * 真实环境调用微信 jscode2session；mockMode=true 时直接派一个固定 unionid/openid，
 * 仅供 dev 与 CI。
 */
@Service
@AllArgsConstructor
public class WechatLoginGatewayImpl implements WechatLoginGateway {
    private static final Logger LOG = LoggerFactory.getLogger(WechatLoginGatewayImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final V2WechatProperties properties;
    private final RestTemplate restTemplate;

    @Override
    public WechatSession exchange(String code) {
        if (StringUtils.isBlank(code)) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }

        if (properties.isMockMode()) {
            LOG.warn("v2 wechat login is in mockMode; do not use in production. code={}", code);
            return new WechatSession("mock-union-" + code, "mock-open-" + code);
        }

        if (StringUtils.isBlank(properties.getAppId()) || StringUtils.isBlank(properties.getAppSecret())) {
            LOG.error("v2 wechat appId/appSecret not configured; refusing to issue session");
            throw new RenException(ErrorCode.NOT_NULL, "v2.wechat.miniapp.app-id/app-secret");
        }

        String url = UriComponentsBuilder.fromHttpUrl(properties.getJscode2sessionUrl())
                .queryParam("appid", properties.getAppId())
                .queryParam("secret", properties.getAppSecret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .build()
                .toUriString();

        String body = restTemplate.getForObject(url, String.class);
        Map<String, Object> parsed = parse(body);
        Object errcode = parsed.get("errcode");
        if (errcode != null && !"0".equals(errcode.toString()) && !Integer.valueOf(0).equals(errcode)) {
            LOG.warn("wechat jscode2session returned error: {}", body);
            throw new RenException(ErrorCode.UNAUTHORIZED);
        }

        String unionid = (String) parsed.get("unionid");
        String openid = (String) parsed.get("openid");
        if (StringUtils.isBlank(unionid)) {
            // 小程序未关联开放平台时拿不到 unionid，业务侧需要保证已关联。
            LOG.warn("wechat session missing unionid: {}", body);
            throw new RenException(ErrorCode.UNAUTHORIZED);
        }
        return new WechatSession(unionid, openid);
    }

    private static Map<String, Object> parse(String body) {
        if (StringUtils.isBlank(body)) {
            return new HashMap<>();
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            Map<String, Object> map = new HashMap<>();
            node.fieldNames().forEachRemaining(name -> map.put(name, node.get(name).asText()));
            return map;
        } catch (Exception e) {
            LOG.warn("parse wechat response failed: {}", body, e);
            throw new RenException(ErrorCode.UNAUTHORIZED);
        }
    }
}
