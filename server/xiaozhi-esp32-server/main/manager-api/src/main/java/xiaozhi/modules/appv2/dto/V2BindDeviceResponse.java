package xiaozhi.modules.appv2.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Minimal v2 bind device response")
public class V2BindDeviceResponse {
    private Long accountId;
    private String deviceId;
    private String bindingStatus;
}
