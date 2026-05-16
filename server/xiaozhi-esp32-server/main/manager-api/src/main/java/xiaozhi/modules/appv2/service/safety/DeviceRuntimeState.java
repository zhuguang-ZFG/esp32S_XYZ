package xiaozhi.modules.appv2.service.safety;

import java.util.Locale;
import java.util.Objects;

public final class DeviceRuntimeState {
    private final boolean homed;
    private final String state;

    public DeviceRuntimeState(boolean homed, String state) {
        this.homed = homed;
        this.state = Objects.requireNonNull(state, "state").toUpperCase(Locale.ROOT);
    }

    public static DeviceRuntimeState of(boolean homed, String state) {
        return new DeviceRuntimeState(homed, state);
    }

    public boolean isHomed() {
        return homed;
    }

    public String getState() {
        return state;
    }

    public boolean isIdle() {
        return "IDLE".equals(state);
    }

    public boolean isEstop() {
        return "ESTOP".equals(state);
    }
}
