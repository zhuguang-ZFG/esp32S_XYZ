package xiaozhi.modules.appv2.service.contentaudit;

public class ContentAuditException extends RuntimeException {
    public static final String ERROR_CODE = "E_CONTENT_BLOCKED";

    private final String errorCode;
    private final String ruleHit;
    private final String auditPath;
    private final String auditRaw;

    public ContentAuditException(String ruleHit) {
        this(ruleHit, null, null);
    }

    public ContentAuditException(String ruleHit, String auditPath, String auditRaw) {
        super(ERROR_CODE);
        this.errorCode = ERROR_CODE;
        this.ruleHit = ruleHit;
        this.auditPath = auditPath;
        this.auditRaw = auditRaw;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getRuleHit() {
        return ruleHit;
    }

    public String getAuditPath() {
        return auditPath;
    }

    public String getAuditRaw() {
        return auditRaw;
    }
}
