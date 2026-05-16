package xiaozhi.modules.appv2.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Primary-session decision for a pending voice task")
public class V2TaskApprovalRequest {
    @Schema(description = "optional decision reason")
    private String reason;
}
