package xiaozhi.modules.appv2.service.contentaudit;

public final class ContentAuditDecision {
    private final boolean allowed;
    private final String ruleHit;

    private ContentAuditDecision(boolean allowed, String ruleHit) {
        this.allowed = allowed;
        this.ruleHit = ruleHit;
    }

    public static ContentAuditDecision allow() {
        return new ContentAuditDecision(true, null);
    }

    public static ContentAuditDecision block(String ruleHit) {
        return new ContentAuditDecision(false, ruleHit);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getRuleHit() {
        return ruleHit;
    }
}
