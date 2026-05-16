package xiaozhi.modules.appv2.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Minimal v2 family member create request")
public class V2MemberCreateRequest {
    @NotBlank
    private String deviceId;

    @NotBlank
    private String displayName;

    @NotBlank
    @Schema(description = "owner|member|child")
    private String memberType;
}
