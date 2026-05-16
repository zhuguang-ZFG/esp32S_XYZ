package xiaozhi.modules.appv2.service.projection;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import javax.imageio.ImageIO;

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
        assertEquals("dashscope_qwen_image", DashScopeDrawGeneratedSvgProvider.PROVIDER_NAME);
        assertEquals("draw_generated_image_vectorize_v1", DashScopeDrawGeneratedSvgProvider.PROJECTION_NAME);
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

    @Test
    void testVectorizerWithSyntheticImage() throws Exception {
        // Create a simple test image: black cross on white background
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_BYTE_GRAY);
        var g = img.getGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 64, 64);
        g.setColor(java.awt.Color.BLACK);
        g.drawLine(10, 32, 54, 32);
        g.drawLine(32, 10, 32, 54);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);

        BitmapToSvgVectorizer vectorizer = new BitmapToSvgVectorizer();
        String svg = vectorizer.vectorize(baos.toByteArray());

        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("<path"));
        assertTrue(svg.contains("M"));
        assertTrue(svg.contains("L"));
    }
}
