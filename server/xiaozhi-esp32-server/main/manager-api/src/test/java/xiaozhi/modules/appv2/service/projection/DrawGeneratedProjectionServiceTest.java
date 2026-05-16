package xiaozhi.modules.appv2.service.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import xiaozhi.modules.appv2.service.graphic.DrawingValidationException;
import xiaozhi.modules.appv2.service.graphic.SingleLineSvgValidator;

class DrawGeneratedProjectionServiceTest {
    private final DrawGeneratedProjectionService service =
            new DrawGeneratedProjectionService(new SingleLineSvgValidator());

    @Test
    void projectsSimpleSingleLineSvgToRunPath() {
        RunPathProjection projection = service.project(Map.of(
                "svg_text",
                "<svg viewBox=\"0 0 10 10\"><path d=\"M0 0 L10 0 L10 10 Z\" fill=\"none\" stroke=\"black\"/></svg>"));

        assertEquals(900, projection.getParams().get("feed"));
        assertEquals("draw_generated_svg_minimal_v1", projection.getMetadata().get("projection"));
        assertEquals(34.142, ((Number) projection.getMetadata().get("total_length_mm")).doubleValue(), 0.001);
        assertEquals(2.276, ((Number) projection.getMetadata().get("estimated_seconds")).doubleValue(), 0.001);
        assertEquals(4, projection.getPath().size());
        assertEquals("M", projection.getPath().get(0).get("cmd"));
        assertEquals("L", projection.getPath().get(3).get("cmd"));
        assertEquals(0.0, ((Number) projection.getPath().get(3).get("x")).doubleValue());
        assertEquals(0.0, ((Number) projection.getPath().get(3).get("y")).doubleValue());
    }

    @Test
    void rejectsMissingSvgText() {
        DrawingValidationException error =
                assertThrows(DrawingValidationException.class, () -> service.project(Map.of()));

        assertEquals("missing_svg_text", error.getReason());
    }

    @Test
    void projectsPromptToDeterministicPlaceholderRunPath() {
        RunPathProjection projection = service.project(Map.of("prompt", "cat"));

        assertEquals(900, projection.getParams().get("feed"));
        assertEquals("draw_generated_prompt_placeholder_v1", projection.getMetadata().get("projection"));
        assertEquals("prompt", projection.getMetadata().get("source"));
        assertEquals("local_fake_ai", projection.getMetadata().get("provider"));
        assertEquals(3, projection.getPath().size());
        assertEquals("M", projection.getPath().get(0).get("cmd"));
        assertEquals("L", projection.getPath().get(2).get("cmd"));
    }

    @Test
    void projectsPromptThroughConfiguredSvgProvider() {
        DrawGeneratedProjectionService service = new DrawGeneratedProjectionService(
                new SingleLineSvgValidator(),
                (prompt, params) -> new GeneratedSvg(
                        "<svg><path d=\"M1 2 L3 4\" fill=\"none\" stroke=\"black\"/></svg>",
                        "draw_generated_fake_provider_test_v1",
                        "prompt",
                        "test_provider"));

        RunPathProjection projection = service.project(Map.of("prompt", "cat"));

        assertEquals("draw_generated_fake_provider_test_v1", projection.getMetadata().get("projection"));
        assertEquals("prompt", projection.getMetadata().get("source"));
        assertEquals("test_provider", projection.getMetadata().get("provider"));
        assertEquals(2, projection.getPath().size());
        assertEquals(1.0, ((Number) projection.getPath().get(0).get("x")).doubleValue());
        assertEquals(4.0, ((Number) projection.getPath().get(1).get("y")).doubleValue());
    }

    @Test
    void rejectsImplicitStarterAssetFallback() {
        DrawingValidationException error = assertThrows(
                DrawingValidationException.class,
                () -> service.project(Map.of("prompt", "cat", "starter_id", "starter_cat")));

        assertEquals("starter_asset_not_explicit", error.getReason());
    }

    @Test
    void projectsExplicitStarterAssetToRunPath() {
        RunPathProjection projection = service.project(Map.of("starter_id", "starter_star", "use_starter_asset", true));

        assertEquals("draw_generated_starter_asset_v1", projection.getMetadata().get("projection"));
        assertEquals("starter_asset", projection.getMetadata().get("source"));
        assertEquals("starter_star", projection.getMetadata().get("asset_id"));
        assertEquals(11, projection.getPath().size());
        assertEquals("M", projection.getPath().get(0).get("cmd"));
        assertEquals("L", projection.getPath().get(10).get("cmd"));
    }

    @Test
    void rejectsUnknownExplicitStarterAsset() {
        DrawingValidationException error = assertThrows(
                DrawingValidationException.class,
                () -> service.project(Map.of("starter_id", "starter_unknown", "use_starter_asset", true)));

        assertEquals("starter_asset_not_found", error.getReason());
    }

    @Test
    void vectorizesSimpleBitmapToRunPath() {
        RunPathProjection projection = service.project(Map.of("bitmap_base64", bitmapPngBase64(false)));

        assertEquals(900, projection.getParams().get("feed"));
        assertEquals("draw_generated_bitmap_vectorize_minimal_v1", projection.getMetadata().get("projection"));
        assertEquals("bitmap", projection.getMetadata().get("source"));
        assertEquals(3, projection.getPath().size());
        assertEquals("M", projection.getPath().get(0).get("cmd"));
        assertEquals("L", projection.getPath().get(2).get("cmd"));
    }

    @Test
    void rejectsBlankBitmap() {
        DrawingValidationException error = assertThrows(
                DrawingValidationException.class,
                () -> service.project(Map.of("bitmap_base64", bitmapPngBase64(true))));

        assertEquals("bitmap_empty", error.getReason());
    }

    @Test
    void rejectsPathThatExceedsTotalLengthLimit() {
        DrawingValidationException error = assertThrows(
                DrawingValidationException.class,
                () -> service.project(Map.of(
                        "svg_text",
                        "<svg><path d=\"M0 0 L6000 0\" fill=\"none\" stroke=\"black\"/></svg>")));

        assertEquals("total_length_exceeded", error.getReason());
    }

    @Test
    void rejectsPathThatExceedsEstimatedTimeLimit() {
        DrawingValidationException error = assertThrows(
                DrawingValidationException.class,
                () -> service.project(Map.of(
                        "feed",
                        1,
                        "svg_text",
                        "<svg><path d=\"M0 0 L20 0\" fill=\"none\" stroke=\"black\"/></svg>")));

        assertEquals("estimated_time_exceeded", error.getReason());
    }

    @Test
    void fitsSvgPathIntoCanvasWithContainCentering() {
        RunPathProjection projection = service.project(Map.of(
                "canvas_width_mm",
                100,
                "canvas_height_mm",
                50,
                "svg_text",
                "<svg><path d=\"M0 0 L10 0 L10 10 Z\" fill=\"none\" stroke=\"black\"/></svg>"));

        assertEquals("canvas_contain_v1", projection.getMetadata().get("layout"));
        assertEquals(4, projection.getPath().size());
        assertEquals(25.0, ((Number) projection.getPath().get(0).get("x")).doubleValue());
        assertEquals(0.0, ((Number) projection.getPath().get(0).get("y")).doubleValue());
        assertEquals(75.0, ((Number) projection.getPath().get(1).get("x")).doubleValue());
        assertEquals(50.0, ((Number) projection.getPath().get(2).get("y")).doubleValue());
    }

    @Test
    void alignsSvgPathToTopLeftWithinCanvas() {
        RunPathProjection projection = service.project(Map.of(
                "align",
                "top_left",
                "canvas_width_mm",
                100,
                "canvas_height_mm",
                50,
                "svg_text",
                "<svg><path d=\"M0 0 L10 0 L10 10 Z\" fill=\"none\" stroke=\"black\"/></svg>"));

        assertEquals(0.0, ((Number) projection.getPath().get(0).get("x")).doubleValue());
        assertEquals(0.0, ((Number) projection.getPath().get(0).get("y")).doubleValue());
        assertEquals(50.0, ((Number) projection.getPath().get(1).get("x")).doubleValue());
        assertEquals(50.0, ((Number) projection.getPath().get(2).get("y")).doubleValue());
    }

    private static String bitmapPngBase64(boolean blank) {
        try {
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    image.setRGB(x, y, Color.WHITE.getRGB());
                }
            }
            if (!blank) {
                image.setRGB(1, 1, Color.BLACK.getRGB());
                image.setRGB(2, 1, Color.BLACK.getRGB());
                image.setRGB(2, 2, Color.BLACK.getRGB());
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
