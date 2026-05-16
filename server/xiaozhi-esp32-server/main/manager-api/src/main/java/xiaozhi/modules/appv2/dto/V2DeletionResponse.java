package xiaozhi.modules.appv2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class V2DeletionResponse {
    private String status;
    private Integer affectedRows;
    private Integer auditRetentionDays;
}
