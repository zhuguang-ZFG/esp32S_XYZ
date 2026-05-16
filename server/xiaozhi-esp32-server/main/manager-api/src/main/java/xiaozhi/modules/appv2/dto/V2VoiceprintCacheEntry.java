package xiaozhi.modules.appv2.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class V2VoiceprintCacheEntry {
    private Long memberId;
    private String displayName;
    private String memberType;
    private String speakerRef;
    private String embeddingHash;
    private String status;
    private Date expiresAt;
}
