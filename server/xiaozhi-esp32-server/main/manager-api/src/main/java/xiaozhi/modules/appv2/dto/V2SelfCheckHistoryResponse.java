package xiaozhi.modules.appv2.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import xiaozhi.modules.appv2.entity.V2DeviceSelfCheckEventEntity;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class V2SelfCheckHistoryResponse {
    private Long id;
    private String deviceId;
    private String checkId;
    private String scope;
    private String status;
    private String summary;
    private String checksJson;
    private Date reportedAt;

    public static V2SelfCheckHistoryResponse fromEntity(V2DeviceSelfCheckEventEntity entity) {
        if (entity == null) {
            return null;
        }
        return new V2SelfCheckHistoryResponse(
                entity.getId(),
                entity.getDeviceId(),
                entity.getCheckId(),
                entity.getScope(),
                entity.getStatus(),
                entity.getSummary(),
                entity.getChecksJson(),
                entity.getReportedAt());
    }
}
