package xiaozhi.modules.appv2.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import xiaozhi.modules.appv2.entity.V2FirmwareReleaseEntity;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class V2FirmwareReleaseResponse {
    private String releaseId;
    private String channel;
    private String version;
    private String url;
    private String sha256;
    private String signature;
    private Integer rolloutPercent;
    private Integer failureThresholdPercent;
    private Integer installCount;
    private Integer failureCount;
    private String status;
    private Date publishedAt;

    public static V2FirmwareReleaseResponse fromEntity(V2FirmwareReleaseEntity entity) {
        if (entity == null) {
            return null;
        }
        return new V2FirmwareReleaseResponse(
                entity.getReleaseId(),
                entity.getChannel(),
                entity.getVersion(),
                entity.getUrl(),
                entity.getSha256(),
                entity.getSignature(),
                entity.getRolloutPercent(),
                entity.getFailureThresholdPercent(),
                entity.getInstallCount(),
                entity.getFailureCount(),
                entity.getStatus(),
                entity.getPublishedAt());
    }
}
