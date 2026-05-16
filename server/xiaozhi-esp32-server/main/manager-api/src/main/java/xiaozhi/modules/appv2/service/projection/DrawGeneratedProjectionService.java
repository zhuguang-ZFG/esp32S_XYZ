package xiaozhi.modules.appv2.service.projection;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import xiaozhi.modules.appv2.service.graphic.DrawingValidationException;
import xiaozhi.modules.appv2.service.graphic.SingleLineSvgValidator;

@Service
public class DrawGeneratedProjectionService {
    private static final Pattern TOKEN = Pattern.compile("[MmLlZz]|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)");
    private static final int DEFAULT_FEED_RATE = 900;
    private static final double MAX_TOTAL_LENGTH_MM = 5000.0;
    private static final double MAX_ESTIMATED_SECONDS = 600.0;
    private static final int MAX_BITMAP_DIMENSION_PX = 128;
    private static final int MAX_BITMAP_DARK_PIXELS = 512;
    private static final String PROMPT_PLACEHOLDER_PROJECTION = "draw_generated_prompt_placeholder_v1";
    private static final String BITMAP_VECTORIZED_PROJECTION = "draw_generated_bitmap_vectorize_minimal_v1";

    private final SingleLineSvgValidator singleLineSvgValidator;
    private final DrawGeneratedSvgProvider svgProvider;
    private final StarterAssetCatalog starterAssetCatalog;

    @Autowired
    public DrawGeneratedProjectionService(
            SingleLineSvgValidator singleLineSvgValidator,
            DrawGeneratedSvgProvider svgProvider,
            StarterAssetCatalog starterAssetCatalog) {
        this.singleLineSvgValidator = singleLineSvgValidator;
        this.svgProvider = svgProvider;
        this.starterAssetCatalog = starterAssetCatalog;
    }

    public DrawGeneratedProjectionService(SingleLineSvgValidator singleLineSvgValidator) {
        this(singleLineSvgValidator, new LocalFakeDrawGeneratedSvgProvider(), new StarterAssetCatalog());
    }

    public DrawGeneratedProjectionService(
            SingleLineSvgValidator singleLineSvgValidator, DrawGeneratedSvgProvider svgProvider) {
        this(singleLineSvgValidator, svgProvider, new StarterAssetCatalog());
    }

    public RunPathProjection project(Map<String, Object> params) {
        String starterAsset = starterAssetParam(params);
        String svgText = stringParam(params, "svg_text");
        String source = "svg_text";
        String projectionName = "draw_generated_svg_minimal_v1";
        String providerName = null;
        if (StringUtils.isNotBlank(starterAsset)) {
            svgText = explicitStarterAssetSvg(params, starterAsset);
            source = "starter_asset";
            projectionName = StarterAssetCatalog.PROJECTION_NAME;
        } else if (StringUtils.isBlank(svgText)) {
            String bitmap = firstStringParam(params, "bitmap_base64", "bitmap_data_uri", "image_base64");
            if (StringUtils.isNotBlank(bitmap)) {
                svgText = vectorizeBitmapSvg(bitmap);
                source = "bitmap";
                projectionName = BITMAP_VECTORIZED_PROJECTION;
            } else {
                String prompt = stringParam(params, "prompt");
                if (StringUtils.isBlank(prompt)) {
                    throw new DrawingValidationException("missing_svg_text");
                }
                GeneratedSvg generated = svgProvider.generate(prompt, params);
                svgText = generated.svgText();
                source = generated.source();
                projectionName = generated.projection();
                providerName = generated.provider();
            }
        }

        String pathData = singleLineSvgValidator.requireSinglePathData(svgText);
        List<PathPoint> path = parsePath(pathData);
        if (path.isEmpty()) {
            throw new DrawingValidationException("empty_path");
        }
        path = layoutPath(path, params);
        int feedRate = feedRateParam(params);
        double totalLengthMm = totalLengthMm(path);
        double estimatedSeconds = totalLengthMm / feedRate * 60.0;
        if (totalLengthMm > MAX_TOTAL_LENGTH_MM) {
            throw new DrawingValidationException("total_length_exceeded");
        }
        if (estimatedSeconds > MAX_ESTIMATED_SECONDS) {
            throw new DrawingValidationException("estimated_time_exceeded");
        }

        Map<String, Object> runPathParams = new LinkedHashMap<>();
        runPathParams.put("feed", feedRate);
        runPathParams.put("path", path.stream().map(PathPoint::toMap).toList());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("projection", projectionName);
        metadata.put("source", source);
        if (StringUtils.isNotBlank(starterAsset)) {
            metadata.put("asset_id", starterAsset);
        }
        if (StringUtils.isNotBlank(providerName)) {
            metadata.put("provider", providerName);
        }
        metadata.put("point_count", path.size());
        metadata.put("total_length_mm", totalLengthMm);
        metadata.put("estimated_seconds", estimatedSeconds);
        metadata.put("layout", hasCanvas(params) ? "canvas_contain_v1" : "source_units");
        return new RunPathProjection(runPathParams, metadata);
    }

    private String explicitStarterAssetSvg(Map<String, Object> params, String starterAsset) {
        if (!booleanParam(params, "use_starter_asset")) {
            throw new DrawingValidationException("starter_asset_not_explicit");
        }
        return starterAssetCatalog.findSvg(starterAsset)
                .orElseThrow(() -> new DrawingValidationException("starter_asset_not_found"));
    }

    private static String starterAssetParam(Map<String, Object> params) {
        return firstStringParam(params, "starter_id", "starter_asset_id", "preset_id");
    }

    private static String vectorizeBitmapSvg(String bitmap) {
        BufferedImage image = decodeBitmap(bitmap);
        if (image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new DrawingValidationException("bitmap_empty");
        }
        if (image.getWidth() > MAX_BITMAP_DIMENSION_PX || image.getHeight() > MAX_BITMAP_DIMENSION_PX) {
            throw new DrawingValidationException("bitmap_too_large");
        }

        List<double[]> points = new ArrayList<>();
        double scale = 90.0 / Math.max(image.getWidth(), image.getHeight());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (isDarkPixel(image.getRGB(x, y))) {
                    points.add(new double[] {5.0 + (x + 0.5) * scale, 5.0 + (y + 0.5) * scale});
                    if (points.size() > MAX_BITMAP_DARK_PIXELS) {
                        throw new DrawingValidationException("bitmap_too_complex");
                    }
                }
            }
        }
        if (points.isEmpty()) {
            throw new DrawingValidationException("bitmap_empty");
        }

        StringBuilder path = new StringBuilder();
        for (int index = 0; index < points.size(); index++) {
            double[] point = points.get(index);
            path.append(index == 0 ? "M" : " L")
                    .append(point[0])
                    .append(' ')
                    .append(point[1]);
        }
        return "<svg viewBox=\"0 0 "
                + image.getWidth()
                + " "
                + image.getHeight()
                + "\"><path d=\""
                + path
                + "\" fill=\"none\" stroke=\"black\"/></svg>";
    }

    private static BufferedImage decodeBitmap(String bitmap) {
        String payload = StringUtils.defaultString(bitmap).trim();
        int comma = payload.indexOf(',');
        if (payload.regionMatches(true, 0, "data:", 0, 5) && comma >= 0) {
            payload = payload.substring(comma + 1);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(payload);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new DrawingValidationException("bitmap_decode");
            }
            return image;
        } catch (IllegalArgumentException e) {
            throw new DrawingValidationException("bitmap_decode");
        } catch (DrawingValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new DrawingValidationException("bitmap_decode");
        }
    }

    private static boolean isDarkPixel(int argb) {
        int alpha = (argb >>> 24) & 0xff;
        if (alpha < 16) {
            return false;
        }
        int red = (argb >>> 16) & 0xff;
        int green = (argb >>> 8) & 0xff;
        int blue = argb & 0xff;
        int luminance = (red * 299 + green * 587 + blue * 114) / 1000;
        return luminance < 128;
    }

    private static List<PathPoint> layoutPath(List<PathPoint> path, Map<String, Object> params) {
        if (!hasCanvas(params)) {
            return path;
        }
        double canvasWidth = doubleParam(params, "canvas_width_mm");
        double canvasHeight = doubleParam(params, "canvas_height_mm");
        if (canvasWidth <= 0.0 || canvasHeight <= 0.0) {
            throw new DrawingValidationException("invalid_canvas");
        }

        Bounds bounds = Bounds.from(path);
        if (bounds.width() <= 0.0 || bounds.height() <= 0.0) {
            throw new DrawingValidationException("invalid_bbox");
        }
        double scale = Math.min(canvasWidth / bounds.width(), canvasHeight / bounds.height());
        double targetWidth = bounds.width() * scale;
        double targetHeight = bounds.height() * scale;
        double originX = doubleParam(params, "origin_x_mm", 0.0);
        double originY = doubleParam(params, "origin_y_mm", 0.0);
        LayoutAlign align = layoutAlign(params);
        originX += align.xOffset(canvasWidth - targetWidth);
        originY += align.yOffset(canvasHeight - targetHeight);

        List<PathPoint> transformed = new ArrayList<>();
        for (PathPoint point : path) {
            double x = originX + (point.getX() - bounds.minX()) * scale;
            double y = originY + (point.getY() - bounds.minY()) * scale;
            if ("M".equals(point.getCmd())) {
                transformed.add(PathPoint.move(x, y, 0.0));
            } else {
                transformed.add(PathPoint.line(x, y, 0.0));
            }
        }
        return transformed;
    }

    private static boolean hasCanvas(Map<String, Object> params) {
        return params != null && params.containsKey("canvas_width_mm") && params.containsKey("canvas_height_mm");
    }

    private static LayoutAlign layoutAlign(Map<String, Object> params) {
        String align = StringUtils.defaultString(firstStringParam(params, "align", "layout_hint")).toLowerCase(Locale.ROOT);
        if (align.contains("top") && align.contains("left")) {
            return LayoutAlign.TOP_LEFT;
        }
        if (align.contains("top") && align.contains("right")) {
            return LayoutAlign.TOP_RIGHT;
        }
        if (align.contains("bottom") && align.contains("left")) {
            return LayoutAlign.BOTTOM_LEFT;
        }
        if (align.contains("bottom") && align.contains("right")) {
            return LayoutAlign.BOTTOM_RIGHT;
        }
        return switch (align) {
            case "left" -> LayoutAlign.LEFT;
            case "right" -> LayoutAlign.RIGHT;
            case "top" -> LayoutAlign.TOP;
            case "bottom" -> LayoutAlign.BOTTOM;
            default -> LayoutAlign.CENTER;
        };
    }

    private static double totalLengthMm(List<PathPoint> path) {
        double total = 0.0;
        PathPoint previous = null;
        for (PathPoint point : path) {
            if (previous != null && "L".equals(point.getCmd())) {
                double dx = point.getX() - previous.getX();
                double dy = point.getY() - previous.getY();
                total += Math.hypot(dx, dy);
            }
            previous = point;
        }
        return total;
    }

    private static List<PathPoint> parsePath(String pathData) {
        List<String> tokens = tokenize(pathData);
        List<PathPoint> points = new ArrayList<>();
        String command = null;
        double startX = 0.0;
        double startY = 0.0;
        double currentX = 0.0;
        double currentY = 0.0;

        int index = 0;
        while (index < tokens.size()) {
            String token = tokens.get(index);
            if (isCommand(token)) {
                command = token;
                index++;
                if ("Z".equalsIgnoreCase(command)) {
                    points.add(PathPoint.line(startX, startY, 0.0));
                }
                continue;
            }
            if (command == null) {
                throw new DrawingValidationException("path_missing_command");
            }
            if (!"M".equalsIgnoreCase(command) && !"L".equalsIgnoreCase(command)) {
                throw new DrawingValidationException("unsupported_path_command");
            }
            if (index + 1 >= tokens.size() || isCommand(tokens.get(index + 1))) {
                throw new DrawingValidationException("path_coordinate_pair");
            }
            double x = parseNumber(tokens.get(index));
            double y = parseNumber(tokens.get(index + 1));
            if (Character.isLowerCase(command.charAt(0))) {
                x += currentX;
                y += currentY;
            }
            if ("M".equalsIgnoreCase(command)) {
                points.add(PathPoint.move(x, y, 0.0));
                startX = x;
                startY = y;
                command = Character.isLowerCase(command.charAt(0)) ? "l" : "L";
            } else {
                points.add(PathPoint.line(x, y, 0.0));
            }
            currentX = x;
            currentY = y;
            index += 2;
        }
        return points;
    }

    private static List<String> tokenize(String pathData) {
        Matcher matcher = TOKEN.matcher(StringUtils.defaultString(pathData));
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private static boolean isCommand(String value) {
        return value.length() == 1 && Character.isLetter(value.charAt(0));
    }

    private static double parseNumber(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new DrawingValidationException("path_number");
        }
    }

    private static String stringParam(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String firstStringParam(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            String value = stringParam(params, key);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean booleanParam(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        return "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private static double doubleParam(Map<String, Object> params, String key) {
        return doubleParam(params, key, 0.0);
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

    private static int feedRateParam(Map<String, Object> params) {
        Object value = params == null ? null : params.get("feed");
        if (value == null && params != null) {
            value = params.get("feed_rate");
        }
        if (value == null) {
            return DEFAULT_FEED_RATE;
        }
        int feedRate;
        if (value instanceof Number number) {
            feedRate = number.intValue();
        } else {
            feedRate = Integer.parseInt(String.valueOf(value).trim());
        }
        if (feedRate <= 0) {
            throw new DrawingValidationException("invalid_feed_rate");
        }
        return feedRate;
    }

    private record Bounds(double minX, double maxX, double minY, double maxY) {
        static Bounds from(List<PathPoint> path) {
            double minX = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (PathPoint point : path) {
                minX = Math.min(minX, point.getX());
                maxX = Math.max(maxX, point.getX());
                minY = Math.min(minY, point.getY());
                maxY = Math.max(maxY, point.getY());
            }
            return new Bounds(minX, maxX, minY, maxY);
        }

        double width() {
            return maxX - minX;
        }

        double height() {
            return maxY - minY;
        }
    }

    private enum LayoutAlign {
        CENTER,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT;

        double xOffset(double extra) {
            return switch (this) {
                case LEFT, TOP_LEFT, BOTTOM_LEFT -> 0.0;
                case RIGHT, TOP_RIGHT, BOTTOM_RIGHT -> extra;
                default -> extra / 2.0;
            };
        }

        double yOffset(double extra) {
            return switch (this) {
                case TOP, TOP_LEFT, TOP_RIGHT -> 0.0;
                case BOTTOM, BOTTOM_LEFT, BOTTOM_RIGHT -> extra;
                default -> extra / 2.0;
            };
        }
    }
}
