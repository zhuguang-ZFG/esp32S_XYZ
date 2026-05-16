package xiaozhi.modules.appv2.service.safety;

public class SafetyValidationException extends RuntimeException {
    private final String errorCode;
    private final String reason;

    public SafetyValidationException(SafetyErrorCode errorCode, String reason) {
        super(errorCode.name());
        this.errorCode = errorCode.name();
        this.reason = reason;
    }

    public SafetyValidationException(SafetyDecision decision) {
        this(decision.getErrorCode(), decision.getMessage());
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getReason() {
        return reason;
    }
}
