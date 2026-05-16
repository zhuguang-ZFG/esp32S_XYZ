package xiaozhi.modules.appv2.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Firmware install result ingestion request")
public class V2FirmwareInstallResultRequest {
    @Schema(description = "true when the device installed and booted the release successfully")
    private Boolean success;
}
