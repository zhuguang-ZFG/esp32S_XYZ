package xiaozhi.modules.appv2.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Minimal v2 device transfer request")
public class V2DeviceTransferRequest {
    @NotBlank
    @Schema(description = "target account wechat unionid")
    private String targetUnionid;
}
