package xiaozhi.modules.appv2.service;

import java.util.List;

import xiaozhi.modules.appv2.dto.V2VoiceprintCacheEntry;
import xiaozhi.modules.appv2.dto.V2VoiceprintEnrollRequest;
import xiaozhi.modules.appv2.dto.V2VoiceprintEnrollResponse;

public interface VoiceprintEnrollmentService {
    V2VoiceprintEnrollResponse enroll(V2VoiceprintEnrollRequest request);

    List<V2VoiceprintCacheEntry> activeCacheForDevice(String deviceId);
}
