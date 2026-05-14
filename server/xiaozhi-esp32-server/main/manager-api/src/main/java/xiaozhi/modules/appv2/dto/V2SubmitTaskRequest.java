package xiaozhi.modules.appv2.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Minimal v2 task submit request")
public class V2SubmitTaskRequest {
    @NotBlank
    @Schema(description = "capability name")
    private String capability;

    @Schema(description = "idempotency request id")
    private String requestId;

    @Schema(description = "trace id")
    private String traceId;

    @Schema(description = "task source")
    private String source;

    @Schema(description = "task params")
    private Map<String, Object> params;

    @Schema(description = "execution constraints")
    private Map<String, Object> constraints;
}
