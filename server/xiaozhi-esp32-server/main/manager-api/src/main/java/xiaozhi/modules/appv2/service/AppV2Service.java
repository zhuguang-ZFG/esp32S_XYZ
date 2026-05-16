package xiaozhi.modules.appv2.service;

import java.util.Map;
import java.util.List;

import xiaozhi.modules.appv2.dto.V2BindDeviceRequest;
import xiaozhi.modules.appv2.dto.V2BindDeviceResponse;
import xiaozhi.modules.appv2.dto.V2LoginRequest;
import xiaozhi.modules.appv2.dto.V2LoginResponse;
import xiaozhi.modules.appv2.dto.V2PendingVoiceTaskResponse;
import xiaozhi.modules.appv2.dto.V2SelfCheckHistoryResponse;
import xiaozhi.modules.appv2.dto.V2SubmitTaskRequest;
import xiaozhi.modules.appv2.dto.V2SubmitTaskResponse;
import xiaozhi.modules.appv2.dto.V2TaskApprovalRequest;

public interface AppV2Service {
    V2LoginResponse login(V2LoginRequest request);

    V2BindDeviceResponse bindDevice(V2BindDeviceRequest request);

    V2SubmitTaskResponse submitTask(String deviceId, V2SubmitTaskRequest request);

    V2SubmitTaskResponse submitVoiceTask(String deviceId, V2SubmitTaskRequest request);

    V2SubmitTaskResponse approveVoiceTask(String taskId, V2TaskApprovalRequest request);

    V2SubmitTaskResponse rejectVoiceTask(String taskId, V2TaskApprovalRequest request);

    List<V2PendingVoiceTaskResponse> listPendingVoiceTasks(String deviceId);

    /**
     * 接收设备经 DeviceServer 转发的 motion_event（M2.6）；落库与五态映射见 M2.9。
     */
    void ingestMotionEvent(Map<String, Object> payload);

    /**
     * 接收设备经 DeviceServer 转发的 device_info（M2.13）；持久化能力快照留给后续 device_caps 表。
     */
    void ingestDeviceInfo(Map<String, Object> payload);

    void ingestSelfCheck(Map<String, Object> payload);

    List<V2SelfCheckHistoryResponse> listSelfCheckHistory(String deviceId);
}
