package xiaozhi.modules.appv2.service.safety;

public final class WorkspaceBounds {
    private final double minX;
    private final double maxX;
    private final double minY;
    private final double maxY;
    private final double minZ;
    private final double maxZ;

    public WorkspaceBounds(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
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

    public static WorkspaceBounds of(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
        return new WorkspaceBounds(minX, maxX, minY, maxY, minZ, maxZ);
    }

    private static void requireOrdered(String axis, double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) {
            throw new IllegalArgumentException(axis + " bounds are invalid");
        }
    }

    public boolean contains(PathBounds pathBounds) {
        return pathBounds.getMinX() >= minX
                && pathBounds.getMaxX() <= maxX
                && pathBounds.getMinY() >= minY
                && pathBounds.getMaxY() <= maxY
                && pathBounds.getMinZ() >= minZ
                && pathBounds.getMaxZ() <= maxZ;
    }

    public WorkspaceBounds shrink(SafeMarginMm margin) {
        return new WorkspaceBounds(
                minX + margin.getX(),
                maxX - margin.getX(),
                minY + margin.getY(),
                maxY - margin.getY(),
                minZ + margin.getZ(),
                maxZ - margin.getZ());
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
