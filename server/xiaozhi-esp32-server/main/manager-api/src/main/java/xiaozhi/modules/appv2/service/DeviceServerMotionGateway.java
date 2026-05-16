package xiaozhi.modules.appv2.service;

import xiaozhi.modules.appv2.dto.V2SubmitTaskRequest;
import xiaozhi.modules.appv2.entity.V2TaskEntity;

/**
 * 将已接受的 motion 任务转发到 DeviceServer（M2.3）。
 */
public interface DeviceServerMotionGateway {

    void forwardAcceptedTask(String deviceId, V2TaskEntity task, V2SubmitTaskRequest request);

    void forwardRuntimeStatusRefresh(String deviceId, Long accountId, String reason, String traceId);

    void clearVoiceprintCache(String deviceId, String reason);
}
