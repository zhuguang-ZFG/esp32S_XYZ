package xiaozhi.modules.appv2.service.graphic;

import java.io.StringReader;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Service
public class SingleLineSvgValidator {
    private static final int MAX_PATH_COMMANDS = 512;
    private static final Pattern PATH_COMMAND = Pattern.compile("[MmLlHhVvCcSsQqTtAaZz]");

    public SvgValidationResult validate(String svgText) {
        if (StringUtils.isBlank(svgText)) {
            return SvgValidationResult.invalid("missing_svg_text", 0, 0);
        }

        Document document = parse(svgText);
        Element root = document.getDocumentElement();
        if (root == null || !"svg".equalsIgnoreCase(root.getLocalName() == null ? root.getNodeName() : root.getLocalName())) {
            return SvgValidationResult.invalid("root_not_svg", 0, 0);
        }
        if (hasFilledShape(root)) {
            return SvgValidationResult.invalid("filled_shape", 0, 0);
        }

        NodeList paths = root.getElementsByTagName("path");
        if (paths.getLength() != 1) {
            return SvgValidationResult.invalid("path_count", paths.getLength(), 0);
        }

        Element path = (Element) paths.item(0);
        if (hasFill(path)) {
            return SvgValidationResult.invalid("filled_path", paths.getLength(), 0);
        }
        if (!hasBlackStroke(path)) {
            return SvgValidationResult.invalid("non_black_stroke", paths.getLength(), 0);
        }

        String d = path.getAttribute("d");
        int commandCount = countPathCommands(d);
        if (commandCount <= 0) {
            return SvgValidationResult.invalid("empty_path", paths.getLength(), commandCount);
        }
        if (commandCount > MAX_PATH_COMMANDS) {
            return SvgValidationResult.invalid("too_many_nodes", paths.getLength(), commandCount);
        }
        return SvgValidationResult.valid(paths.getLength(), commandCount);
    }

    public void requireValid(String svgText) {
        SvgValidationResult result = validate(svgText);
        if (!result.isValid()) {
            throw new DrawingValidationException(result.getReason());
        }
    }

    public String requireSinglePathData(String svgText) {
        requireValid(svgText);
        Document document = parse(svgText);
        NodeList paths = document.getDocumentElement().getElementsByTagName("path");
        return ((Element) paths.item(0)).getAttribute("d");
    }

    private static Document parse(String svgText) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(svgText)));
            document.getDocumentElement().normalize();
            return document;
        } catch (Exception e) {
            throw new DrawingValidationException("invalid_xml");
        }
    }

    private static boolean hasFilledShape(Element root) {
        String[] filledTags = {"circle", "ellipse", "rect", "polygon"};
        for (String tag : filledTags) {
            NodeList nodes = root.getElementsByTagName(tag);
            for (int index = 0; index < nodes.getLength(); index++) {
                if (nodes.item(index) instanceof Element element && hasFill(element)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasFill(Element element) {
        String fill = element.getAttribute("fill");
        if (StringUtils.isBlank(fill)) {
            return false;
        }
        return !"none".equalsIgnoreCase(fill.trim());
    }

    private static boolean hasBlackStroke(Element element) {
        String stroke = element.getAttribute("stroke");
        if (StringUtils.isBlank(stroke)) {
            return false;
        }
        String normalized = stroke.trim().toLowerCase(Locale.ROOT);
        return "black".equals(normalized) || "#000".equals(normalized) || "#000000".equals(normalized);
    }

    private static int countPathCommands(String pathData) {
        if (StringUtils.isBlank(pathData)) {
            return 0;
        }
        Matcher matcher = PATH_COMMAND.matcher(pathData);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
