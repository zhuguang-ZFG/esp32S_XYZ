package xiaozhi.modules.appv2.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Minimal v2 task submit response")
public class V2SubmitTaskResponse {
    private String taskId;
    private String status;
}
