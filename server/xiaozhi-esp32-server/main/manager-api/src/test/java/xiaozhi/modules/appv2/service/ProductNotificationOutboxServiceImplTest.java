package xiaozhi.modules.appv2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.modules.appv2.dao.V2ProductNotificationEventDao;
import xiaozhi.modules.appv2.entity.V2ProductNotificationEventEntity;
import xiaozhi.modules.appv2.service.impl.ProductNotificationOutboxServiceImpl;

@ExtendWith(MockitoExtension.class)
class ProductNotificationOutboxServiceImplTest {
    @Mock
    private V2ProductNotificationEventDao v2ProductNotificationEventDao;

    @Test
    void enqueuePendingDeviceTransferStoresSafeDeepLinkOnly() {
        ProductNotificationOutboxServiceImpl service =
                new ProductNotificationOutboxServiceImpl(v2ProductNotificationEventDao);

        service.enqueuePendingDeviceTransfer(42L, "dev-1", 7L);

        ArgumentCaptor<V2ProductNotificationEventEntity> captor =
                ArgumentCaptor.forClass(V2ProductNotificationEventEntity.class);
        verify(v2ProductNotificationEventDao).insert(captor.capture());
        V2ProductNotificationEventEntity event = captor.getValue();
        assertEquals("pending_device_transfer", event.getEventType());
        assertEquals(42L, event.getRecipientAccountId());
        assertEquals("dev-1", event.getDeviceId());
        assertEquals("device_transfer", event.getTargetRefType());
        assertEquals("7", event.getTargetRefId());
        assertEquals("/pages/v2/device-list/index", event.getDeepLink());
        assertEquals("pending", event.getStatus());
        assertNotNull(event.getCreatedAt());
    }

    @Test
    void enqueuePendingPrimaryVoiceApprovalStoresSafeTargetFieldsOnly() {
        ProductNotificationOutboxServiceImpl service =
                new ProductNotificationOutboxServiceImpl(v2ProductNotificationEventDao);

        service.enqueuePendingPrimaryVoiceApproval(41L, "dev-voice-1", "task-1");

        ArgumentCaptor<V2ProductNotificationEventEntity> captor =
                ArgumentCaptor.forClass(V2ProductNotificationEventEntity.class);
        verify(v2ProductNotificationEventDao).insert(captor.capture());
        V2ProductNotificationEventEntity event = captor.getValue();
        assertEquals("pending_primary_voice_approval", event.getEventType());
        assertEquals(41L, event.getRecipientAccountId());
        assertEquals("dev-voice-1", event.getDeviceId());
        assertEquals("task", event.getTargetRefType());
        assertEquals("task-1", event.getTargetRefId());
        assertEquals("/pages/v2/device-detail/index?deviceId=dev-voice-1", event.getDeepLink());
        assertEquals("pending", event.getStatus());
    }

    @Test
    void enqueuePendingPrimaryVoiceApprovalEncodesDeviceIdQueryParam() {
        ProductNotificationOutboxServiceImpl service =
                new ProductNotificationOutboxServiceImpl(v2ProductNotificationEventDao);

        service.enqueuePendingPrimaryVoiceApproval(41L, "dev voice&1", "task-1");

        ArgumentCaptor<V2ProductNotificationEventEntity> captor =
                ArgumentCaptor.forClass(V2ProductNotificationEventEntity.class);
        verify(v2ProductNotificationEventDao).insert(captor.capture());
        V2ProductNotificationEventEntity event = captor.getValue();
        assertEquals("/pages/v2/device-detail/index?deviceId=dev%20voice%261", event.getDeepLink());
        assertFalse(event.getDeepLink().contains("dev voice&1"));
    }

    @Test
    void buildSafeProviderPayloadExposesOnlySemanticOutboxFields() {
        ProductNotificationOutboxServiceImpl service =
                new ProductNotificationOutboxServiceImpl(v2ProductNotificationEventDao);
        V2ProductNotificationEventEntity event = new V2ProductNotificationEventEntity();
        event.setEventType("pending_primary_voice_approval");
        event.setRecipientAccountId(41L);
        event.setDeviceId("dev-voice-1");
        event.setTargetRefType("task");
        event.setTargetRefId("task-1");
        event.setDeepLink("/pages/v2/device-detail/index?deviceId=dev-voice-1");
        event.setStatus("pending");

        Map<String, String> payload = service.buildSafeProviderPayload(event);

        assertEquals(5, payload.size());
        assertEquals("pending_primary_voice_approval", payload.get("event"));
        assertEquals("dev-voice-1", payload.get("device_id"));
        assertEquals("task", payload.get("target_ref_type"));
        assertEquals("task-1", payload.get("target_ref_id"));
        assertEquals("/pages/v2/device-detail/index?deviceId=dev-voice-1", payload.get("deep_link"));
        assertFalse(payload.containsKey("recipient_account_id"));
        assertFalse(payload.containsKey("status"));
        assertFalse(payload.containsKey("created_at"));
        String renderedPayload = payload.toString().toLowerCase();
        assertFalse(renderedPayload.contains("prompt"));
        assertFalse(renderedPayload.contains("transcript"));
        assertFalse(renderedPayload.contains("biometric"));
        assertFalse(renderedPayload.contains("activation"));
        assertFalse(renderedPayload.contains("capability"));
    }

    @Test
    void providerResultUpdatesOnlyPendingEventsById() {
        ProductNotificationOutboxServiceImpl service =
                new ProductNotificationOutboxServiceImpl(v2ProductNotificationEventDao);

        service.markProviderSent(11L);
        service.markProviderFailed(12L);

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<UpdateWrapper<V2ProductNotificationEventEntity>> captor =
                ArgumentCaptor.forClass((Class) UpdateWrapper.class);
        verify(v2ProductNotificationEventDao, times(2)).update(isNull(), captor.capture());
        String sql = captor.getAllValues().stream()
                .map(wrapper -> wrapper.getSqlSegment() + " " + wrapper.getSqlSet())
                .reduce("", (left, right) -> left + "\n" + right);
        String params = captor.getAllValues().stream()
                .map(wrapper -> wrapper.getParamNameValuePairs().values().toString())
                .reduce("", (left, right) -> left + "\n" + right);
        assertContains(sql, "id");
        assertContains(sql, "status");
        assertContains(sql, "sent_at");
        assertContains(sql, "updated_at");
        assertContains(params, "11");
        assertContains(params, "12");
        assertContains(params, "sent");
        assertContains(params, "failed");
        assertContains(params, "pending");
    }

    @Test
    void enqueueSkipsIncompleteNotificationTargets() {
        ProductNotificationOutboxServiceImpl service =
                new ProductNotificationOutboxServiceImpl(v2ProductNotificationEventDao);

        service.enqueuePendingDeviceTransfer(null, "dev-1", 7L);
        service.enqueuePendingDeviceTransfer(42L, " ", 7L);
        service.enqueuePendingDeviceTransfer(42L, "dev-1", null);
        service.enqueuePendingPrimaryVoiceApproval(null, "dev-1", "task-1");
        service.enqueuePendingPrimaryVoiceApproval(42L, " ", "task-1");
        service.enqueuePendingPrimaryVoiceApproval(42L, "dev-1", "");

        verify(v2ProductNotificationEventDao, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolveAndCancelUpdateOnlyPendingEventsByTargetReference() {
        ProductNotificationOutboxServiceImpl service =
                new ProductNotificationOutboxServiceImpl(v2ProductNotificationEventDao);

        service.resolveDeviceTransfer(7L);
        service.cancelDeviceTransfer(8L);
        service.resolvePrimaryVoiceApproval("task-1");
        service.cancelPrimaryVoiceApproval("task-2");

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<UpdateWrapper<V2ProductNotificationEventEntity>> captor =
                ArgumentCaptor.forClass((Class) UpdateWrapper.class);
        verify(v2ProductNotificationEventDao, times(4)).update(isNull(), captor.capture());
        String sql = captor.getAllValues().stream()
                .map(wrapper -> wrapper.getSqlSegment() + " " + wrapper.getSqlSet())
                .reduce("", (left, right) -> left + "\n" + right);
        String params = captor.getAllValues().stream()
                .map(wrapper -> wrapper.getParamNameValuePairs().values().toString())
                .reduce("", (left, right) -> left + "\n" + right);
        assertContains(sql, "target_ref_type");
        assertContains(sql, "target_ref_id");
        assertContains(sql, "status");
        assertContains(sql, "updated_at");
        assertContains(params, "device_transfer");
        assertContains(params, "task");
        assertContains(params, "7");
        assertContains(params, "8");
        assertContains(params, "task-1");
        assertContains(params, "task-2");
        assertContains(params, "resolved");
        assertContains(params, "cancelled");
        assertContains(params, "pending");
        assertFalse((sql + params).contains("prompt"));
        assertFalse((sql + params).contains("transcript"));
    }

    @Test
    void lifecycleUpdatesIgnoreBlankTargets() {
        ProductNotificationOutboxServiceImpl service =
                new ProductNotificationOutboxServiceImpl(v2ProductNotificationEventDao);

        service.resolveDeviceTransfer(null);
        service.cancelDeviceTransfer(null);
        service.resolvePrimaryVoiceApproval(" ");
        service.cancelPrimaryVoiceApproval(null);
        service.markProviderSent(null);
        service.markProviderSent(0L);
        service.markProviderFailed(null);
        service.markProviderFailed(-1L);

        verify(v2ProductNotificationEventDao, never()).update(isNull(), org.mockito.ArgumentMatchers.any());
    }

    private static void assertContains(String text, String token) {
        org.junit.jupiter.api.Assertions.assertTrue(text.contains(token), () -> "missing token: " + token + "\n" + text);
    }
}
