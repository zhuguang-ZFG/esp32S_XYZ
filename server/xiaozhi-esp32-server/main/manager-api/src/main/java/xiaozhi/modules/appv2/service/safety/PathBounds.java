package xiaozhi.modules.appv2.service.safety;

public final class PathBounds {
    private final double minX;
    private final double maxX;
    private final double minY;
    private final double maxY;
    private final double minZ;
    private final double maxZ;

    public PathBounds(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
        requireOrdered("x", minX, maxX);
        requireOrdered("y", minY, maxY);
        requireOrdered("z", minZ, maxZ);
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    public static PathBounds of(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
        return new PathBounds(minX, maxX, minY, maxY, minZ, maxZ);
    }

    private static void requireOrdered(String axis, double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) {
            throw new IllegalArgumentException(axis + " path bounds are invalid");
        }
    }

    public double getMinX() {
        return minX;
    }

    public double getMaxX() {
        return maxX;
    }

    public double getMinY() {
        return minY;
    }

    public double getMaxY() {
        return maxY;
    }

    public double getMinZ() {
        return minZ;
    }

    public double getMaxZ() {
        return maxZ;
    }
}
