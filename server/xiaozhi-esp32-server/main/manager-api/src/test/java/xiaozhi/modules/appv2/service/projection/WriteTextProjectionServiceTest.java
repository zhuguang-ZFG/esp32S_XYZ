package xiaozhi.modules.appv2.service.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class WriteTextProjectionServiceTest {
    private final WriteTextProjectionService service = new WriteTextProjectionService();

    @Test
    void projectsNiHaoToRunPathInsideDefaultCanvas() {
        RunPathProjection projection = service.project(Map.of("text", "你好"));

        assertEquals(WriteTextProjectionService.DEFAULT_FEED_RATE, projection.getParams().get("feed"));
        assertEquals("write_text_minimal_v1", projection.getMetadata().get("projection"));
        assertEquals("kai_basic_v1", projection.getMetadata().get("font_id"));
        assertEquals(14, projection.getPath().size());
        assertFalse(projection.getPath().isEmpty());
        assertEquals("M", projection.getPath().get(0).get("cmd"));
        assertEquals(39.0, ((Number) projection.getPath().get(0).get("x")).doubleValue());
        assertEquals(18.0, ((Number) projection.getPath().get(0).get("y")).doubleValue());
    }

    @Test
    void rejectsBlankText() {
        assertThrows(IllegalArgumentException.class, () -> service.project(Map.of("text", " ")));
    }
}
