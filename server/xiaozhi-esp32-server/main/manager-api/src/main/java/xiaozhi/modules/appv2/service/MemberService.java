package xiaozhi.modules.appv2.service;

import java.util.List;

import xiaozhi.modules.appv2.dto.V2MemberCreateRequest;
import xiaozhi.modules.appv2.dto.V2MemberResponse;

public interface MemberService {
    V2MemberResponse create(V2MemberCreateRequest request);

    List<V2MemberResponse> listByDevice(String deviceId);
}
