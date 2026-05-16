package xiaozhi.modules.appv2.service.safety;

import java.util.Objects;

public final class DeviceCaps {
    private final WorkspaceBounds workspace;
    private final SafeMarginMm safeMargin;
    private final double maxFeedRate;

    public DeviceCaps(WorkspaceBounds workspace, SafeMarginMm safeMargin, double maxFeedRate) {
        if (!Double.isFinite(maxFeedRate) || maxFeedRate <= 0.0) {
            throw new IllegalArgumentException("maxFeedRate is invalid");
        }
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.safeMargin = Objects.requireNonNull(safeMargin, "safeMargin");
        this.maxFeedRate = maxFeedRate;
    }

    public static DeviceCaps of(WorkspaceBounds workspace, SafeMarginMm safeMargin, double maxFeedRate) {
        return new DeviceCaps(workspace, safeMargin, maxFeedRate);
    }

    public WorkspaceBounds getWorkspace() {
        return workspace;
    }

    public SafeMarginMm getSafeMargin() {
        return safeMargin;
    }

    public double getMaxFeedRate() {
        return maxFeedRate;
    }

    public WorkspaceBounds writableBounds() {
        return workspace.shrink(safeMargin);
    }
}
