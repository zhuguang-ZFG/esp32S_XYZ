package xiaozhi.modules.appv2.service.projection;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import xiaozhi.modules.appv2.config.V2DashScopeProperties;

@Service
@ConditionalOnProperty(prefix = "v2.dashscope", name = "enabled", havingValue = "true")
public class DashScopeDrawGeneratedSvgProvider implements DrawGeneratedSvgProvider {

    private static final Logger log = LoggerFactory.getLogger(DashScopeDrawGeneratedSvgProvider.class);
    public static final String PROVIDER_NAME = "dashscope_qwen";
    public static final String PROJECTION_NAME = "draw_generated_qwen_single_line_v1";

    private static final String SYSTEM_PROMPT = """
            你是一个简笔画SVG生成器。用户给你一个主题词，你输出一个20x20 viewBox的SVG。
            规则：
            - 只输出一个<svg>标签，内含一个<path>元素
            - 只用M（移动）和L（直线）命令，坐标为整数或一位小数
            - 路径必须是连续单线，适合笔绘机一笔画完
            - fill="none" stroke="black"
            - 点数控制在10~30个之间，画出可辨认的轮廓
            - 只输出SVG代码，不要任何解释文字""";

    private static final Pattern SVG_PATTERN = Pattern.compile("<svg[^>]*>.*?</svg>", Pattern.DOTALL);

    private final V2DashScopeProperties properties;
    private final RestTemplate restTemplate;

    public DashScopeDrawGeneratedSvgProvider(V2DashScopeProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public GeneratedSvg generate(String prompt, Map<String, Object> params) {
        try {
            String svg = callQwen(prompt);
            return new GeneratedSvg(svg, PROJECTION_NAME, "prompt", PROVIDER_NAME);
        } catch (Exception e) {
            log.warn("DashScope generation failed for prompt='{}': {}", prompt, e.getMessage());
            throw new xiaozhi.modules.appv2.service.graphic.DrawingValidationException("ai_generation_failed");
        }
    }

    @SuppressWarnings("unchecked")
    private String callQwen(String prompt) {
        String url = properties.getBaseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + properties.getApiKey());

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", prompt)),
                "temperature", 0.7);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new RuntimeException("empty response");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("no choices in response");
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String content = (String) message.get("content");
        return extractSvg(content);
    }

    private static String extractSvg(String content) {
        if (StringUtils.isBlank(content)) {
            throw new RuntimeException("empty content from LLM");
        }
        Matcher matcher = SVG_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group();
        }
        if (content.contains("<path")) {
            return "<svg viewBox=\"0 0 20 20\">" + content.trim() + "</svg>";
        }
        throw new RuntimeException("no SVG found in LLM response");
    }
}