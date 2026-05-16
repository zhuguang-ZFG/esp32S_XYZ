package xiaozhi.modules.appv2.service.safety;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class SafetyValidator {
    private static final Set<String> HOMING_EXEMPT_CAPABILITIES = Set.of("home", "get_status", "get_device_info");

    public SafetyDecision validate(String capability, DeviceCaps caps, DeviceRuntimeState runtimeState, PathBounds pathBounds) {
        return validate(capability, caps, runtimeState, pathBounds, null);
    }

    public SafetyDecision validate(
            String capability,
            DeviceCaps caps,
            DeviceRuntimeState runtimeState,
            PathBounds pathBounds,
            Double feedRate) {
        Objects.requireNonNull(caps, "caps");
        Objects.requireNonNull(runtimeState, "runtimeState");

        String normalizedCapability = normalizeCapability(capability);

        if (runtimeState.isEstop()) {
            return SafetyDecision.reject(SafetyErrorCode.E_ESTOP, "device is in ESTOP");
        }

        if (requiresHomed(normalizedCapability) && !runtimeState.isHomed()) {
            return SafetyDecision.reject(SafetyErrorCode.E_NOT_HOMED, "device must be homed before " + normalizedCapability);
        }

        if (!runtimeState.isIdle()) {
            return SafetyDecision.reject(SafetyErrorCode.E_DEVICE_BUSY, "device state is " + runtimeState.getState());
        }

        if (pathBounds != null && !caps.writableBounds().contains(pathBounds)) {
            return SafetyDecision.reject(SafetyErrorCode.E_OUT_OF_RANGE, "path bounds exceed writable area");
        }

        if (feedRate != null && (!Double.isFinite(feedRate) || feedRate <= 0.0 || feedRate > caps.getMaxFeedRate())) {
            return SafetyDecision.reject(SafetyErrorCode.E_INVALID_PARAM, "feed_rate exceeds device max_feed_rate");
        }

        return SafetyDecision.allow();
    }

    private static boolean requiresHomed(String capability) {
        return !HOMING_EXEMPT_CAPABILITIES.contains(capability);
    }

    private static String normalizeCapability(String capability) {
        if (capability == null || capability.isBlank()) {
            throw new IllegalArgumentException("capability is required");
        }
        return capability.trim().toLowerCase(Locale.ROOT);
    }
}
