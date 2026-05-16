package xiaozhi.modules.appv2.service;

public class PrimarySessionException extends RuntimeException {
    private final String code;

    public PrimarySessionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
