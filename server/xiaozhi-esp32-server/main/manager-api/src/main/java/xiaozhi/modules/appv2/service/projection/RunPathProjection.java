package xiaozhi.modules.appv2.service.projection;

import java.util.List;
import java.util.Map;

public final class RunPathProjection {
    private final Map<String, Object> params;
    private final Map<String, Object> metadata;

    public RunPathProjection(Map<String, Object> params, Map<String, Object> metadata) {
        this.params = Map.copyOf(params);
        this.metadata = Map.copyOf(metadata);
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPath() {
        return (List<Map<String, Object>>) params.get("path");
    }
}
