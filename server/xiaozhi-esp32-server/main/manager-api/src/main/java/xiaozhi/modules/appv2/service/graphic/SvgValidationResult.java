package xiaozhi.modules.appv2.service.graphic;

public final class SvgValidationResult {
    private final boolean valid;
    private final String reason;
    private final int pathCount;
    private final int commandCount;

    private SvgValidationResult(boolean valid, String reason, int pathCount, int commandCount) {
        this.valid = valid;
        this.reason = reason;
        this.pathCount = pathCount;
        this.commandCount = commandCount;
    }

    public static SvgValidationResult valid(int pathCount, int commandCount) {
        return new SvgValidationResult(true, null, pathCount, commandCount);
    }

    public static SvgValidationResult invalid(String reason, int pathCount, int commandCount) {
        return new SvgValidationResult(false, reason, pathCount, commandCount);
    }

    public boolean isValid() {
        return valid;
    }

    public String getReason() {
        return reason;
    }

    public int getPathCount() {
        return pathCount;
    }

    public int getCommandCount() {
        return commandCount;
    }
}
