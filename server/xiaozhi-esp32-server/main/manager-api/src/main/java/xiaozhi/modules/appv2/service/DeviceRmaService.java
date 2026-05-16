package xiaozhi.modules.appv2.service;

import java.util.List;

import xiaozhi.modules.appv2.dto.V2DeviceRmaRequest;
import xiaozhi.modules.appv2.dto.V2DeviceRmaResponse;
import xiaozhi.modules.appv2.entity.V2DeviceRmaEventEntity;

public interface DeviceRmaService {
    V2DeviceRmaResponse startRepair(String deviceId, V2DeviceRmaRequest request);

    V2DeviceRmaResponse completeRepair(String deviceId, V2DeviceRmaRequest request);

    V2DeviceRmaResponse confirmReturn(String deviceId, V2DeviceRmaRequest request);

    V2DeviceRmaResponse restockReturned(String deviceId, V2DeviceRmaRequest request);

    V2DeviceRmaResponse disposeDevice(String deviceId, V2DeviceRmaRequest request);

    List<V2DeviceRmaEventEntity> listAuditEvents(String deviceId);
}
