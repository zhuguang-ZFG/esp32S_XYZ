package xiaozhi.modules.appv2.dto;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Manual v2 device consumable state update")
public class V2DeviceSupplyUpdateRequest {
    @Schema(description = "empty|loaded|unknown")
    private String paperSlotState;
    @Schema(description = "latest pen replacement time")
    private Date penInstalledAt;
    @Schema(description = "estimated ink percent, 0-100")
    private Integer penInkPercentEst;
    @Schema(description = "reset pen mileage to zero when replacing pen")
    private Boolean resetPenMileage;
}
