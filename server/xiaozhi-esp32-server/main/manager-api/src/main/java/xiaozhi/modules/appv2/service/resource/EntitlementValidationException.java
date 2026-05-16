package xiaozhi.modules.appv2.service.resource;

import lombok.Getter;

@Getter
public class EntitlementValidationException extends RuntimeException {
    public static final String ERROR_CODE = "E_NOT_ENTITLED";

    private final String errorCode;
    private final String resourceType;
    private final String resourceId;

    public EntitlementValidationException(String resourceType, String resourceId) {
        super(ERROR_CODE + ": " + resourceType + ":" + resourceId);
        this.errorCode = ERROR_CODE;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
}
