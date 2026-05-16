package xiaozhi.modules.appv2.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Design-time firmware release publish request")
public class V2FirmwareReleasePublishRequest {
    private String releaseId;
    private String version;
    private String url;
    private String sha256;
    private String signature;
    private Integer rolloutPercent;
    private Integer failureThresholdPercent;
}
