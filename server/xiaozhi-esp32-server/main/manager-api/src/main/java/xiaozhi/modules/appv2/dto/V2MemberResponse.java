package xiaozhi.modules.appv2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class V2MemberResponse {
    private Long memberId;
    private String deviceId;
    private String displayName;
    private String role;
    private String memberType;
    private String status;
}
