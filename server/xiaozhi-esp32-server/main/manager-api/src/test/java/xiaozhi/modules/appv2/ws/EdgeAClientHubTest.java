package xiaozhi.modules.appv2.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import cn.hutool.json.JSONUtil;

class EdgeAClientHubTest {

    @Test
    void publishMotionEventBroadcastsToDeviceAndTaskTopics() throws Exception {
        EdgeAClientHub hub = new EdgeAClientHub();
        WebSocketSession rawSession = mock(WebSocketSession.class);
        WebSocketSession sendSession = mock(WebSocketSession.class);
        when(sendSession.isOpen()).thenReturn(true);

        hub.subscribe(rawSession, sendSession, EdgeAClientHub.TOPIC_DEVICE_PREFIX + "dev-1");
        hub.subscribe(rawSession, sendSession, EdgeAClientHub.TOPIC_TASK_PREFIX + "task-1");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("device_id", "dev-1");
        payload.put("task_id", "task-1");
        payload.put("phase", "running");
        payload.put("capability", "home");

        hub.publishMotionEvent(payload);

        verify(sendSession, times(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void publishMotionEventBuildsEdgeAEventEnvelope() throws Exception {
        EdgeAClientHub hub = new EdgeAClientHub();
        WebSocketSession rawSession = mock(WebSocketSession.class);
        WebSocketSession sendSession = mock(WebSocketSession.class);
        when(sendSession.isOpen()).thenReturn(true);

        hub.subscribe(rawSession, sendSession, EdgeAClientHub.TOPIC_DEVICE_PREFIX + "dev-1");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("device_id", "dev-1");
        payload.put("task_id", "task-1");
        payload.put("phase", "done");
        payload.put("capability", "move");
        payload.put("source", "voice");
        payload.put("progress", Map.of("done_segments", 1, "total_segments", 4, "percent", 25));
        payload.put("tts_hint", Map.of("tts_event", "task_done", "text", "done"));

        final TextMessage[] captured = new TextMessage[1];
        org.mockito.Mockito.doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            return null;
        }).when(sendSession).sendMessage(any(TextMessage.class));

        hub.publishMotionEvent(payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = JSONUtil.toBean(captured[0].getPayload(), Map.class);
        assertEquals("event", envelope.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) envelope.get("event");
        assertEquals("job_status", event.get("event_type"));
        assertEquals("dev-1", event.get("device_id"));
        assertEquals("task-1", event.get("task_id"));
        assertEquals(1L, ((Number) event.get("seq")).longValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> eventPayload = (Map<String, Object>) event.get("payload");
        assertEquals("done", eventPayload.get("phase"));
        assertEquals("move", eventPayload.get("capability"));
        assertEquals("voice", eventPayload.get("source"));
        @SuppressWarnings("unchecked")
        Map<String, Object> progress = (Map<String, Object>) eventPayload.get("progress");
        assertEquals(1, ((Number) progress.get("done_segments")).intValue());
        assertEquals(4, ((Number) progress.get("total_segments")).intValue());
        assertEquals(25, ((Number) progress.get("percent")).intValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> ttsHint = (Map<String, Object>) eventPayload.get("tts_hint");
        assertEquals("task_done", ttsHint.get("tts_event"));
    }

    @Test
    void replaySinceReplaysBufferedEventsAfterSequence() throws Exception {
        EdgeAClientHub hub = new EdgeAClientHub();
        WebSocketSession rawSession = mock(WebSocketSession.class);
        WebSocketSession liveSession = mock(WebSocketSession.class);
        WebSocketSession replaySession = mock(WebSocketSession.class);
        when(liveSession.isOpen()).thenReturn(true);
        when(replaySession.isOpen()).thenReturn(true);

        hub.subscribe(rawSession, liveSession, EdgeAClientHub.TOPIC_DEVICE_PREFIX + "dev-1");

        Map<String, Object> first = new LinkedHashMap<>();
        first.put("device_id", "dev-1");
        first.put("task_id", "task-1");
        first.put("phase", "running");
        first.put("capability", "home");
        hub.publishMotionEvent(first);

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("device_id", "dev-1");
        second.put("task_id", "task-2");
        second.put("phase", "done");
        second.put("capability", "home");
        hub.publishMotionEvent(second);

        hub.replaySince(replaySession, EdgeAClientHub.TOPIC_DEVICE_PREFIX + "dev-1", 1L);

        org.mockito.ArgumentCaptor<TextMessage> captor = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(replaySession, times(1)).sendMessage(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = JSONUtil.toBean(captor.getValue().getPayload(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) envelope.get("event");
        assertEquals("task-2", event.get("task_id"));
        assertEquals(2L, ((Number) event.get("seq")).longValue());
    }

    @Test
    void publishDeviceInfoBuildsEdgeADeviceInfoReplyEnvelope() throws Exception {
        EdgeAClientHub hub = new EdgeAClientHub();
        WebSocketSession rawSession = mock(WebSocketSession.class);
        WebSocketSession sendSession = mock(WebSocketSession.class);
        when(sendSession.isOpen()).thenReturn(true);

        hub.subscribe(rawSession, sendSession, EdgeAClientHub.TOPIC_DEVICE_PREFIX + "dev-1");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("device_id", "dev-1");
        payload.put("task_id", "task-info");
        payload.put("model", "DLC Motor Control P1 XYYZ");
        payload.put("hw_rev", "DLC_Motor_Control_P1_V1.0_260513");
        payload.put("fw_rev", "fake-u1");
        payload.put("workspace_mm", Map.of("x", 200.0, "y", 150.0, "z", 50.0));
        payload.put("tts_hint", Map.of("tts_event", "device_info_reply", "text", "info"));

        final TextMessage[] captured = new TextMessage[1];
        org.mockito.Mockito.doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            return null;
        }).when(sendSession).sendMessage(any(TextMessage.class));

        hub.publishDeviceInfo(payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = JSONUtil.toBean(captured[0].getPayload(), Map.class);
        assertEquals("event", envelope.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) envelope.get("event");
        assertEquals("device_info_reply", event.get("event_type"));
        assertEquals("dev-1", event.get("device_id"));
        assertEquals("task-info", event.get("task_id"));
        assertEquals(1L, ((Number) event.get("seq")).longValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> eventPayload = (Map<String, Object>) event.get("payload");
        assertEquals("DLC Motor Control P1 XYYZ", eventPayload.get("model"));
        assertEquals("DLC_Motor_Control_P1_V1.0_260513", eventPayload.get("hw_rev"));
        assertEquals("fake-u1", eventPayload.get("fw_rev"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ttsHint = (Map<String, Object>) eventPayload.get("tts_hint");
        assertEquals("device_info_reply", ttsHint.get("tts_event"));
    }

    @Test
    void publishSelfCheckBuildsEdgeADeviceEventEnvelope() throws Exception {
        EdgeAClientHub hub = new EdgeAClientHub();
        WebSocketSession rawSession = mock(WebSocketSession.class);
        WebSocketSession sendSession = mock(WebSocketSession.class);
        when(sendSession.isOpen()).thenReturn(true);

        hub.subscribe(rawSession, sendSession, EdgeAClientHub.TOPIC_DEVICE_PREFIX + "dev-1");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("device_id", "dev-1");
        payload.put("check_id", "startup");
        payload.put("scope", "startup");
        payload.put("status", "failed");
        payload.put("checks", Map.of(
                "nvs", Map.of("ok", true),
                "wifi", Map.of("ok", true),
                "u1_uart", Map.of("ok", false),
                "audio", Map.of("ok", true)));

        final TextMessage[] captured = new TextMessage[1];
        org.mockito.Mockito.doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            return null;
        }).when(sendSession).sendMessage(any(TextMessage.class));

        hub.publishSelfCheck(payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = JSONUtil.toBean(captured[0].getPayload(), Map.class);
        assertEquals("event", envelope.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) envelope.get("event");
        assertEquals("self_check", event.get("event_type"));
        assertEquals("dev-1", event.get("device_id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> eventPayload = (Map<String, Object>) event.get("payload");
        assertEquals("startup", eventPayload.get("check_id"));
        assertEquals("failed", eventPayload.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> checks = (Map<String, Object>) eventPayload.get("checks");
        @SuppressWarnings("unchecked")
        Map<String, Object> u1 = (Map<String, Object>) checks.get("u1_uart");
        assertEquals(false, u1.get("ok"));
    }
}
