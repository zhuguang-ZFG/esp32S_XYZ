package xiaozhi.modules.appv2.dto;

import java.math.BigDecimal;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class V2DeviceSupplyResponse {
    private String deviceId;
    private String paperSlotState;
    private Date penInstalledAt;
    private Integer penInkPercentEst;
    private BigDecimal penMileageMm;
}
