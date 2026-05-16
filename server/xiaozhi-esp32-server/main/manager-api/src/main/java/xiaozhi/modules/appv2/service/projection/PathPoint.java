package xiaozhi.modules.appv2.service.projection;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PathPoint {
    private final String cmd;
    private final double x;
    private final double y;
    private final double z;

    private PathPoint(String cmd, double x, double y, double z) {
        this.cmd = cmd;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static PathPoint move(double x, double y, double z) {
        return new PathPoint("M", x, y, z);
    }

    public static PathPoint line(double x, double y, double z) {
        return new PathPoint("L", x, y, z);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("cmd", cmd);
        value.put("x", x);
        value.put("y", y);
        value.put("z", z);
        return value;
    }

    public String getCmd() {
        return cmd;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }
}
