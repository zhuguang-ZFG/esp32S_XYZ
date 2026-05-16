package xiaozhi.modules.config.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Device runtime status query")
public class DeviceRuntimeStatusDTO {

    @NotBlank(message = "deviceId cannot be blank")
    @Schema(description = "Device id or device serial/MAC reported by DeviceServer")
    private String deviceId;
}
