package xiaozhi.modules.appv2.service.graphic;

public class DrawingValidationException extends RuntimeException {
    public static final String ERROR_CODE = "E_INVALID_DRAWING";

    private final String errorCode;
    private final String reason;

    public DrawingValidationException(String reason) {
        super(ERROR_CODE);
        this.errorCode = ERROR_CODE;
        this.reason = reason;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getReason() {
        return reason;
    }
}
