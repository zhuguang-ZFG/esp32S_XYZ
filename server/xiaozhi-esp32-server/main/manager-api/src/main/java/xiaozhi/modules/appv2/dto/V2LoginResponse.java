package xiaozhi.modules.appv2.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Minimal v2 login response")
public class V2LoginResponse {
    private Long accountId;
    private String token;
    private Integer expire;
}
