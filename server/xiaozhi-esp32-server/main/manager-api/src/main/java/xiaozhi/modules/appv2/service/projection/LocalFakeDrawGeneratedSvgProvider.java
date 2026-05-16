package xiaozhi.modules.appv2.service.projection;

import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "v2.dashscope", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalFakeDrawGeneratedSvgProvider implements DrawGeneratedSvgProvider {
    public static final String PROVIDER_NAME = "local_fake_ai";
    public static final String PROJECTION_NAME = "draw_generated_prompt_placeholder_v1";

    @Override
    public GeneratedSvg generate(String prompt, Map<String, Object> params) {
        return new GeneratedSvg(promptPlaceholderSvg(prompt), PROJECTION_NAME, "prompt", PROVIDER_NAME);
    }

    private static String promptPlaceholderSvg(String prompt) {
        String normalized = StringUtils.defaultString(prompt).trim().toLowerCase(Locale.ROOT);
        int hash = normalized.hashCode() & 0x7fffffff;
        double peakX = 8.0 + (hash % 5);
        double peakY = 6.0 + ((hash / 5) % 4);
        double tailX = 16.0 + ((hash / 19) % 3);
        double tailY = 12.0 + ((hash / 29) % 3);
        return "<svg viewBox=\"0 0 20 20\"><path d=\"M5 15 L"
                + peakX
                + " "
                + peakY
                + " L"
                + tailX
                + " "
                + tailY
                + "\" fill=\"none\" stroke=\"black\"/></svg>";
    }
}
