package xiaozhi.modules.appv2.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Minimal v2 voiceprint enrollment request")
public class V2VoiceprintEnrollRequest {
    @NotBlank
    private String deviceId;

    @NotBlank
    private String displayName;

    @Schema(description = "owner|member|child")
    private String memberType;

    @NotBlank
    @Schema(description = "base64 encoded enrollment audio, not persisted")
    private String audioBase64;

    @Schema(description = "audio duration in milliseconds")
    private Integer sampleDurationMs;
}
