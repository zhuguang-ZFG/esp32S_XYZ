package xiaozhi.modules.appv2.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Minimal v2 login request")
public class V2LoginRequest {
    @NotBlank
    @Schema(description = "wechat wx.login() temporary code; server exchanges it via jscode2session")
    private String code;

    @Schema(description = "display name override; optional")
    private String displayName;
}
