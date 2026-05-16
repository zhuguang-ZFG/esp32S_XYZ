package xiaozhi.modules.appv2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class V2DeviceTransferResponse {
    private Long transferId;
    private String deviceId;
    private Long sourceAccountId;
    private Long targetAccountId;
    private String status;
}
