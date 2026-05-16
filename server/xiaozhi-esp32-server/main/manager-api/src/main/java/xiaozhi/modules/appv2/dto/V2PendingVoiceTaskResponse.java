package xiaozhi.modules.appv2.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class V2PendingVoiceTaskResponse {
    private String taskId;
    private String deviceId;
    private String requestId;
    private String capability;
    private String paramsJson;
    private String constraintsJson;
    private String status;
    private Date createdAt;
}
