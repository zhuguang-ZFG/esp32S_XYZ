package xiaozhi.modules.appv2.service.projection;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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
    public static final String PROVIDER_NAME = "dashscope_qwen_image";
    public static final String PROJECTION_NAME = "draw_generated_image_vectorize_v1";

    private static final String DRAWING_SUFFIX =
            "，简笔画风格，黑色线条（粗细均匀约2-3px），纯白背景，"
            + "线条清晰连续不交叉不粘连，线条间距适当，"
            + "主体居中占画面70%，无灰度无阴影无渐变无填充无文字，"
            + "轮廓优先内部从简，适合笔绘机单线绘制";

    private final V2DashScopeProperties properties;
    private final RestTemplate restTemplate;
    private final BitmapToSvgVectorizer vectorizer = new BitmapToSvgVectorizer();

    public DashScopeDrawGeneratedSvgProvider(V2DashScopeProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public GeneratedSvg generate(String prompt, Map<String, Object> params) {
        try {
            String imageUrl = generateImage(prompt);
            byte[] imageBytes = downloadImage(imageUrl);
            String svg = vectorizer.vectorize(imageBytes);
            return new GeneratedSvg(svg, PROJECTION_NAME, "prompt", PROVIDER_NAME);
        } catch (Exception e) {
            log.warn("Image generation failed for prompt='{}': {}", prompt, e.getMessage());
            throw new xiaozhi.modules.appv2.service.graphic.DrawingValidationException("ai_generation_failed");
        }
    }

    @SuppressWarnings("unchecked")
    private String generateImage(String prompt) {
        String url = properties.getBaseUrl() + "/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + properties.getApiKey());

        String fullPrompt = prompt + DRAWING_SUFFIX;
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", List.of(Map.of("role", "user", "content",
                        List.of(Map.of("type", "text", "text", fullPrompt)))),
                "parameters", Map.of("size", "512*512"));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) throw new RuntimeException("empty response");

        Map<String, Object> output = (Map<String, Object>) responseBody.get("output");
        List<Map<String, Object>> choices = (List<Map<String, Object>>) output.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");
        return (String) content.get(0).get("image");
    }

    private byte[] downloadImage(String imageUrl) {
        return restTemplate.getForObject(imageUrl, byte[].class);
    }
}