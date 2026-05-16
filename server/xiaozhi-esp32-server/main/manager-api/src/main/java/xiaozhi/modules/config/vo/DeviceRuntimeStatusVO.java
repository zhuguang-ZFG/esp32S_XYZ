package xiaozhi.modules.config.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeviceRuntimeStatusVO {
    private String deviceId;
    private String status;
    private boolean known;
    private boolean disposed;
}
