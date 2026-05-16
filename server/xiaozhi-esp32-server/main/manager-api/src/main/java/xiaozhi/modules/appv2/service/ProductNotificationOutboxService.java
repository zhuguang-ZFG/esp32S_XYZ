package xiaozhi.modules.appv2.service;

import java.util.Map;

import xiaozhi.modules.appv2.entity.V2ProductNotificationEventEntity;

public interface ProductNotificationOutboxService {
    void enqueuePendingDeviceTransfer(Long recipientAccountId, String deviceId, Long transferId);

    void enqueuePendingPrimaryVoiceApproval(Long recipientAccountId, String deviceId, String taskId);

    Map<String, String> buildSafeProviderPayload(V2ProductNotificationEventEntity event);

    void markProviderSent(Long eventId);

    void markProviderFailed(Long eventId);

    void resolveDeviceTransfer(Long transferId);

    void cancelDeviceTransfer(Long transferId);

    void resolvePrimaryVoiceApproval(String taskId);

    void cancelPrimaryVoiceApproval(String taskId);
}
