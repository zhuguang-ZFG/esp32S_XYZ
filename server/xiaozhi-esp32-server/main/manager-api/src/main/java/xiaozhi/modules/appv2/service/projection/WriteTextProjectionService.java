package xiaozhi.modules.appv2.service.projection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class WriteTextProjectionService {
    public static final String DEFAULT_FONT_ID = "kai_basic_v1";
    public static final double DEFAULT_CANVAS_WIDTH_MM = 100.0;
    public static final double DEFAULT_CANVAS_HEIGHT_MM = 50.0;
    public static final int DEFAULT_FEED_RATE = 900;

    private static final double GLYPH_WIDTH_MM = 10.0;
    private static final double GLYPH_HEIGHT_MM = 14.0;
    private static final double GLYPH_GAP_MM = 2.0;
    private static final double PEN_Z_MM = 0.0;

    public RunPathProjection project(Map<String, Object> params) {
        String text = textParam(params);
        String fontId = stringParam(params, "font_id", DEFAULT_FONT_ID);
        double canvasWidth = doubleParam(params, "canvas_width_mm", DEFAULT_CANVAS_WIDTH_MM);
        double canvasHeight = doubleParam(params, "canvas_height_mm", DEFAULT_CANVAS_HEIGHT_MM);
        int feedRate = intParam(params, "feed", DEFAULT_FEED_RATE);

        List<PathPoint> points = new ArrayList<>();
        double totalWidth = text.length() * GLYPH_WIDTH_MM + Math.max(0, text.length() - 1) * GLYPH_GAP_MM;
        double originX = Math.max(0.0, (canvasWidth - totalWidth) / 2.0);
        double originY = Math.max(0.0, (canvasHeight - GLYPH_HEIGHT_MM) / 2.0);

        for (int index = 0; index < text.length(); index++) {
            double x = originX + index * (GLYPH_WIDTH_MM + GLYPH_GAP_MM);
            appendBoxGlyph(points, x, originY);
        }

        Map<String, Object> runPathParams = new LinkedHashMap<>();
        runPathParams.put("feed", feedRate);
        runPathParams.put("path", points.stream().map(PathPoint::toMap).toList());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("projection", "write_text_minimal_v1");
        metadata.put("text", text);
        metadata.put("font_id", fontId);
        metadata.put("canvas_width_mm", canvasWidth);
        metadata.put("canvas_height_mm", canvasHeight);
        metadata.put("glyph_count", text.length());

        return new RunPathProjection(runPathParams, metadata);
    }

    private static void appendBoxGlyph(List<PathPoint> points, double x, double y) {
        points.add(PathPoint.move(x, y, PEN_Z_MM));
        points.add(PathPoint.line(x + GLYPH_WIDTH_MM, y, PEN_Z_MM));
        points.add(PathPoint.line(x + GLYPH_WIDTH_MM, y + GLYPH_HEIGHT_MM, PEN_Z_MM));
        points.add(PathPoint.line(x, y + GLYPH_HEIGHT_MM, PEN_Z_MM));
        points.add(PathPoint.line(x, y, PEN_Z_MM));
        points.add(PathPoint.move(x + GLYPH_WIDTH_MM * 0.25, y + GLYPH_HEIGHT_MM * 0.5, PEN_Z_MM));
        points.add(PathPoint.line(x + GLYPH_WIDTH_MM * 0.75, y + GLYPH_HEIGHT_MM * 0.5, PEN_Z_MM));
    }

    private static String textParam(Map<String, Object> params) {
        String value = stringParam(params, "text", null);
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("write_text requires params.text");
        }
        return value;
    }

    private static String stringParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.defaultIfBlank(text, defaultValue);
    }

    private static double doubleParam(Map<String, Object> params, String key, double defaultValue) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value).trim());
    }

    private static int intParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value).trim().toLowerCase(Locale.ROOT));
    }
}
