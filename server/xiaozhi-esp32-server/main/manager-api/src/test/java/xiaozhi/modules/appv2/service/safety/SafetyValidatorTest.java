package xiaozhi.modules.appv2.service.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SafetyValidatorTest {
    private final SafetyValidator validator = new SafetyValidator();

    @Test
    void rejectsMotionWhenDeviceIsNotHomed() {
        SafetyDecision decision = validator.validate(
                "run_path",
                caps(),
                DeviceRuntimeState.of(false, "IDLE"),
                insideWritableArea());

        assertFalse(decision.isAllowed());
        assertEquals(SafetyErrorCode.E_NOT_HOMED, decision.getErrorCode());
    }

    @Test
    void rejectsPathOutsideWritableAreaAfterSafeMargin() {
        SafetyDecision decision = validator.validate(
                "run_path",
                caps(),
                DeviceRuntimeState.of(true, "IDLE"),
                PathBounds.of(4.9, 80.0, 10.0, 80.0, 1.0, 9.0));

        assertFalse(decision.isAllowed());
        assertEquals(SafetyErrorCode.E_OUT_OF_RANGE, decision.getErrorCode());
    }

    @Test
    void rejectsWhenDeviceIsBusy() {
        SafetyDecision decision = validator.validate(
                "run_path",
                caps(),
                DeviceRuntimeState.of(true, "RUNNING"),
                insideWritableArea());

        assertFalse(decision.isAllowed());
        assertEquals(SafetyErrorCode.E_DEVICE_BUSY, decision.getErrorCode());
    }

    @Test
    void rejectsWhenDeviceIsEstop() {
        SafetyDecision decision = validator.validate(
                "run_path",
                caps(),
                DeviceRuntimeState.of(false, "ESTOP"),
                insideWritableArea());

        assertFalse(decision.isAllowed());
        assertEquals(SafetyErrorCode.E_ESTOP, decision.getErrorCode());
    }

    @Test
    void allowsValidPathInWritableArea() {
        SafetyDecision decision = validator.validate(
                "run_path",
                caps(),
                DeviceRuntimeState.of(true, "IDLE"),
                insideWritableArea());

        assertTrue(decision.isAllowed());
        assertNull(decision.getErrorCode());
    }

    @Test
    void rejectsFeedRateAboveDeviceLimit() {
        SafetyDecision decision = validator.validate(
                "run_path",
                caps(),
                DeviceRuntimeState.of(true, "IDLE"),
                insideWritableArea(),
                3000.1);

        assertFalse(decision.isAllowed());
        assertEquals(SafetyErrorCode.E_INVALID_PARAM, decision.getErrorCode());
    }

    @Test
    void rejectsNonPositiveFeedRate() {
        SafetyDecision decision = validator.validate(
                "run_path",
                caps(),
                DeviceRuntimeState.of(true, "IDLE"),
                insideWritableArea(),
                0.0);

        assertFalse(decision.isAllowed());
        assertEquals(SafetyErrorCode.E_INVALID_PARAM, decision.getErrorCode());
    }

    @Test
    void allowsHomeWithoutHomedFlagWhenDeviceIsIdle() {
        SafetyDecision decision = validator.validate(
                "home",
                caps(),
                DeviceRuntimeState.of(false, "IDLE"),
                null);

        assertTrue(decision.isAllowed());
        assertNull(decision.getErrorCode());
    }

    private static DeviceCaps caps() {
        return DeviceCaps.of(
                WorkspaceBounds.of(0.0, 100.0, 0.0, 100.0, 0.0, 10.0),
                SafeMarginMm.of(5.0, 5.0, 1.0),
                3000.0);
    }

    private static PathBounds insideWritableArea() {
        return PathBounds.of(5.0, 95.0, 5.0, 95.0, 1.0, 9.0);
    }
}
