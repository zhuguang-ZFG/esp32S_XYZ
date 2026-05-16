package xiaozhi.modules.appv2.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import lombok.AllArgsConstructor;
import xiaozhi.modules.appv2.dao.V2ProductNotificationEventDao;
import xiaozhi.modules.appv2.entity.V2ProductNotificationEventEntity;
import xiaozhi.modules.appv2.service.ProductNotificationOutboxService;

@Service
@AllArgsConstructor
public class ProductNotificationOutboxServiceImpl implements ProductNotificationOutboxService {
    static final String EVENT_PENDING_DEVICE_TRANSFER = "pending_device_transfer";
    static final String EVENT_PENDING_PRIMARY_VOICE_APPROVAL = "pending_primary_voice_approval";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_SENT = "sent";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_RESOLVED = "resolved";
    private static final String STATUS_CANCELLED = "cancelled";

    private final V2ProductNotificationEventDao v2ProductNotificationEventDao;

    @Override
    public void enqueuePendingDeviceTransfer(Long recipientAccountId, String deviceId, Long transferId) {
        if (recipientAccountId == null || StringUtils.isBlank(deviceId) || transferId == null) {
            return;
        }
        V2ProductNotificationEventEntity event = baseEvent(
                EVENT_PENDING_DEVICE_TRANSFER,
                recipientAccountId,
                deviceId,
                "device_transfer",
                String.valueOf(transferId),
                "/pages/v2/device-list/index");
        v2ProductNotificationEventDao.insert(event);
    }

    @Override
    public void enqueuePendingPrimaryVoiceApproval(
            Long recipientAccountId,
            String deviceId,
            String taskId) {
        if (recipientAccountId == null || StringUtils.isAnyBlank(deviceId, taskId)) {
            return;
        }
        V2ProductNotificationEventEntity event = baseEvent(
                EVENT_PENDING_PRIMARY_VOICE_APPROVAL,
                recipientAccountId,
                deviceId,
                "task",
                taskId,
                "/pages/v2/device-detail/index?deviceId=" + encodeQueryParam(deviceId));
        v2ProductNotificationEventDao.insert(event);
    }

    @Override
    public Map<String, String> buildSafeProviderPayload(V2ProductNotificationEventEntity event) {
        Map<String, String> payload = new LinkedHashMap<>();
        if (event == null) {
            return payload;
        }
        putIfNotBlank(payload, "event", event.getEventType());
        putIfNotBlank(payload, "device_id", event.getDeviceId());
        putIfNotBlank(payload, "target_ref_type", event.getTargetRefType());
        putIfNotBlank(payload, "target_ref_id", event.getTargetRefId());
        putIfNotBlank(payload, "deep_link", event.getDeepLink());
        return payload;
    }

    @Override
    public void markProviderSent(Long eventId) {
        markProviderResult(eventId, STATUS_SENT, true);
    }

    @Override
    public void markProviderFailed(Long eventId) {
        markProviderResult(eventId, STATUS_FAILED, false);
    }

    @Override
    public void resolveDeviceTransfer(Long transferId) {
        markByTarget("device_transfer", transferId == null ? null : String.valueOf(transferId), STATUS_RESOLVED);
    }

    @Override
    public void cancelDeviceTransfer(Long transferId) {
        markByTarget("device_transfer", transferId == null ? null : String.valueOf(transferId), STATUS_CANCELLED);
    }

    @Override
    public void resolvePrimaryVoiceApproval(String taskId) {
        markByTarget("task", taskId, STATUS_RESOLVED);
    }

    @Override
    public void cancelPrimaryVoiceApproval(String taskId) {
        markByTarget("task", taskId, STATUS_CANCELLED);
    }

    private static V2ProductNotificationEventEntity baseEvent(
            String eventType,
            Long recipientAccountId,
            String deviceId,
            String targetRefType,
            String targetRefId,
            String deepLink) {
        V2ProductNotificationEventEntity event = new V2ProductNotificationEventEntity();
        event.setEventType(eventType);
        event.setRecipientAccountId(recipientAccountId);
        event.setDeviceId(deviceId);
        event.setTargetRefType(targetRefType);
        event.setTargetRefId(targetRefId);
        event.setDeepLink(deepLink);
        event.setStatus(STATUS_PENDING);
        event.setCreatedAt(new Date());
        return event;
    }

    private void markProviderResult(Long eventId, String status, boolean sent) {
        if (eventId == null || eventId <= 0 || StringUtils.isBlank(status)) {
            return;
        }
        Date now = new Date();
        UpdateWrapper<V2ProductNotificationEventEntity> wrapper = new UpdateWrapper<V2ProductNotificationEventEntity>()
                .eq("id", eventId)
                .eq("status", STATUS_PENDING)
                .set("status", status)
                .set("updated_at", now);
        if (sent) {
            wrapper.set("sent_at", now);
        }
        v2ProductNotificationEventDao.update(null, wrapper);
    }

    private void markByTarget(String targetRefType, String targetRefId, String status) {
        if (StringUtils.isAnyBlank(targetRefType, targetRefId, status)) {
            return;
        }
        v2ProductNotificationEventDao.update(null, new UpdateWrapper<V2ProductNotificationEventEntity>()
                .eq("target_ref_type", targetRefType)
                .eq("target_ref_id", targetRefId)
                .eq("status", STATUS_PENDING)
                .set("status", status)
                .set("updated_at", new Date()));
    }

    private static String encodeQueryParam(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }

    private static void putIfNotBlank(Map<String, String> payload, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            payload.put(key, value);
        }
    }
}
