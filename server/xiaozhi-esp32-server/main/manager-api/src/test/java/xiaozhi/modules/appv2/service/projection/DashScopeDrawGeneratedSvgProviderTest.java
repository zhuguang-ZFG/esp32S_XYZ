package xiaozhi.modules.appv2.service.projection;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import xiaozhi.modules.appv2.config.V2DashScopeProperties;
import xiaozhi.modules.appv2.service.graphic.DrawingValidationException;

class DashScopeDrawGeneratedSvgProviderTest {

    @Test
    void testProviderThrowsWhenApiKeyMissing() {
        V2DashScopeProperties props = new V2DashScopeProperties();
        props.setEnabled(true);
        props.setApiKey("");
        props.setBaseUrl("http://localhost:19999/fake");
        props.setTimeoutSeconds(2);

        DashScopeDrawGeneratedSvgProvider provider = new DashScopeDrawGeneratedSvgProvider(props);

        assertThrows(DrawingValidationException.class, () -> {
            provider.generate("画一只猫", Map.of());
        });
    }

    @Test
    void testProviderNameAndProjection() {
        assertEquals("dashscope_qwen", DashScopeDrawGeneratedSvgProvider.PROVIDER_NAME);
        assertEquals("draw_generated_qwen_single_line_v1", DashScopeDrawGeneratedSvgProvider.PROJECTION_NAME);
    }

    @Test
    void testLocalFakeStillWorksWhenDashScopeDisabled() {
        LocalFakeDrawGeneratedSvgProvider fake = new LocalFakeDrawGeneratedSvgProvider();
        GeneratedSvg result = fake.generate("星星", Map.of());

        assertNotNull(result.svgText());
        assertTrue(result.svgText().contains("<svg"));
        assertTrue(result.svgText().contains("<path"));
        assertEquals("local_fake_ai", result.provider());
    }
}
