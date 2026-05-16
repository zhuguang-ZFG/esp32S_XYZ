package xiaozhi.modules.appv2.service.safety;

public final class SafeMarginMm {
    private final double x;
    private final double y;
    private final double z;

    public SafeMarginMm(double x, double y, double z) {
        requireNonNegative("x", x);
        requireNonNegative("y", y);
        requireNonNegative("z", z);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static SafeMarginMm of(double x, double y, double z) {
        return new SafeMarginMm(x, y, z);
    }

    private static void requireNonNegative(String axis, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(axis + " safe margin is invalid");
        }
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }
}
