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
}
