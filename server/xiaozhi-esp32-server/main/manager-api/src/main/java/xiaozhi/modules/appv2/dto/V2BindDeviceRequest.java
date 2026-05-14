package xiaozhi.modules.appv2.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Minimal v2 bind device request")
public class V2BindDeviceRequest {
    @NotBlank
    @Schema(description = "device serial number")
    private String deviceSn;

    @NotBlank
    @Schema(description = "activation code")
    private String activationCode;
}
