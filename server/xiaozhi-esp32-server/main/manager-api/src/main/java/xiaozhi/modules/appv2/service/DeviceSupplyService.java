package xiaozhi.modules.appv2.service;

import xiaozhi.modules.appv2.dto.V2DeviceSupplyResponse;
import xiaozhi.modules.appv2.dto.V2DeviceSupplyUpdateRequest;

public interface DeviceSupplyService {
    V2DeviceSupplyResponse updateSupplies(String deviceId, V2DeviceSupplyUpdateRequest request);

    void requirePaperReadyForWrite(String deviceId, String capability);

    void recordCompletedRunPathMileage(String deviceId, String paramsJson);
}
