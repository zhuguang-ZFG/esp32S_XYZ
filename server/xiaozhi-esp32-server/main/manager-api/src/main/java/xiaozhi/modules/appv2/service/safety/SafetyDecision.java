package xiaozhi.modules.appv2.service.safety;

public final class SafetyDecision {
    private final boolean allowed;
    private final SafetyErrorCode errorCode;
    private final String message;

    private SafetyDecision(boolean allowed, SafetyErrorCode errorCode, String message) {
        this.allowed = allowed;
        this.errorCode = errorCode;
        this.message = message;
    }

    public static SafetyDecision allow() {
        return new SafetyDecision(true, null, "allowed");
    }

    public static SafetyDecision reject(SafetyErrorCode errorCode, String message) {
        return new SafetyDecision(false, errorCode, message);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public SafetyErrorCode getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }
}
