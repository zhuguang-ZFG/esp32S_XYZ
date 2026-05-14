package xiaozhi.modules.appv2.service;

import xiaozhi.modules.appv2.dto.V2BindDeviceRequest;
import xiaozhi.modules.appv2.dto.V2BindDeviceResponse;
import xiaozhi.modules.appv2.dto.V2LoginRequest;
import xiaozhi.modules.appv2.dto.V2LoginResponse;
import xiaozhi.modules.appv2.dto.V2SubmitTaskRequest;
import xiaozhi.modules.appv2.dto.V2SubmitTaskResponse;

public interface AppV2Service {
    V2LoginResponse login(V2LoginRequest request);

    V2BindDeviceResponse bindDevice(V2BindDeviceRequest request);

    V2SubmitTaskResponse submitTask(String deviceId, V2SubmitTaskRequest request);
}
