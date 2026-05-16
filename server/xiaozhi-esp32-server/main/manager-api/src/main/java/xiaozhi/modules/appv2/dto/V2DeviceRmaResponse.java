package xiaozhi.modules.appv2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class V2DeviceRmaResponse {
    private String deviceId;
    private Long accountId;
    private String deviceStatus;
    private String bindingStatus;
    private String activationCode;
}
