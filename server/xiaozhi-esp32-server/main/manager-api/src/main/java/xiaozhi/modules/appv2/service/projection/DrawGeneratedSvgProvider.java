package xiaozhi.modules.appv2.service.projection;

import java.util.Map;

public interface DrawGeneratedSvgProvider {
    GeneratedSvg generate(String prompt, Map<String, Object> params);
}
