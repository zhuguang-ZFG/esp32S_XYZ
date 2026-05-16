package xiaozhi.modules.appv2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class V2VoiceprintEnrollResponse {
    private Long memberId;
    private Long voiceprintId;
    private String status;
    private String speakerRef;
}
