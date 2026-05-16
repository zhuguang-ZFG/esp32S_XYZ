package xiaozhi.modules.appv2.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Minimal v2 device RMA transition request")
public class V2DeviceRmaRequest {
    @Schema(description = "optional after-sales note")
    private String note;
    @Schema(description = "optional new activation code when restocking a returned device")
    private String activationCode;
    @Schema(description = "true only after factory credential cleaning is completed")
    private Boolean factoryCleaned;
    @Schema(description = "external ticket or evidence reference for factory cleaning")
    private String evidenceRef;
    @Schema(description = "external RMA ticket reference for audit correlation")
    private String ticketRef;
}
