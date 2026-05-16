package xiaozhi.modules.appv2.ws;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Edge-A：按 topic 维护 WSS 订阅，并向 ingest 入口广播 JSON 文本帧（M2.7）。
 */
@Slf4j
@Component
public class EdgeAClientHub {
    private static final int MAX_BUFFERED_EVENTS_PER_TOPIC = 200;

    public static final String TOPIC_DEVICE_PREFIX = "device:";
    public static final String TOPIC_TASK_PREFIX = "task:";

    private final ConcurrentHashMap<String, Set<WebSocketSession>> topicToSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocketSession, Set<String>> rawSessionToTopics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocketSession, ConcurrentHashMap<String, Long>> rawSessionAckSeq =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, NavigableMap<Long, String>> topicEvents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> deviceSeq = new ConcurrentHashMap<>();

    public long nextSeq(String deviceId) {
        return deviceSeq.computeIfAbsent(deviceId, k -> new AtomicLong()).incrementAndGet();
    }

    public void subscribe(WebSocketSession rawSession, WebSocketSession sendSession, String topic) {
        topicToSessions.computeIfAbsent(topic, t -> ConcurrentHashMap.newKeySet()).add(sendSession);
        rawSessionToTopics.computeIfAbsent(rawSession, r -> ConcurrentHashMap.newKeySet()).add(topic);
        rawSessionAckSeq.computeIfAbsent(rawSession, r -> new ConcurrentHashMap<>()).putIfAbsent(topic, 0L);
    }

    public void unsubscribe(WebSocketSession rawSession, WebSocketSession sendSession, String topic) {
        Set<WebSocketSession> set = topicToSessions.get(topic);
        if (set != null) {
            set.remove(sendSession);
        }
        Set<String> topics = rawSessionToTopics.get(rawSession);
        if (topics != null) {
            topics.remove(topic);
        }
        ConcurrentHashMap<String, Long> ackSeq = rawSessionAckSeq.get(rawSession);
        if (ackSeq != null) {
            ackSeq.remove(topic);
        }
    }

    public void unsubscribeAll(WebSocketSession rawSession, WebSocketSession sendSession) {
        Set<String> topics = rawSessionToTopics.remove(rawSession);
        ConcurrentHashMap<String, Long> ackSeq = rawSessionAckSeq.remove(rawSession);
        if (topics == null) {
            return;
        }
        for (String topic : topics) {
            Set<WebSocketSession> set = topicToSessions.get(topic);
            if (set != null) {
                set.remove(sendSession);
            }
            if (ackSeq != null) {
                ackSeq.remove(topic);
            }
        }
    }

    public boolean isSubscribed(WebSocketSession rawSession, String topic) {
        Set<String> topics = rawSessionToTopics.get(rawSession);
        return topics != null && topics.contains(topic);
    }

    public long ack(WebSocketSession rawSession, String topic, long seq) {
        ConcurrentHashMap<String, Long> ackSeq = rawSessionAckSeq.get(rawSession);
        if (ackSeq == null || !ackSeq.containsKey(topic)) {
            throw new IllegalStateException("topic not subscribed");
        }
        ackSeq.compute(topic, (k, current) -> {
            long existing = current == null ? 0L : current.longValue();
            return Math.max(existing, seq);
        });
        return ackSeq.getOrDefault(topic, 0L);
    }

    public void replaySince(WebSocketSession sendSession, String topic, long sinceSeq) {
        NavigableMap<Long, String> events = topicEvents.get(topic);
        if (events == null || events.isEmpty()) {
            return;
        }
        for (String json : events.tailMap(sinceSeq, false).values()) {
            try {
                synchronized (sendSession) {
                    sendSession.sendMessage(new TextMessage(json));
                }
            }
            catch (Exception e) {
                log.debug("Edge-A replay skip session: {}", e.getMessage());
                continue;
            }
        }
    }

    private void remember(String topic, long seq, String json) {
        topicEvents.compute(topic, (k, existing) -> {
            NavigableMap<Long, String> events = existing == null ? new TreeMap<>() : existing;
            events.put(seq, json);
            while (events.size() > MAX_BUFFERED_EVENTS_PER_TOPIC) {
                events.pollFirstEntry();
            }
            return events;
        });
    }

    public void broadcast(String topic, String json) {
        Set<WebSocketSession> set = topicToSessions.get(topic);
        if (set == null || set.isEmpty()) {
            return;
        }
        TextMessage msg = new TextMessage(json);
        for (WebSocketSession s : set) {
            if (s == null || !s.isOpen()) {
                continue;
            }
            try {
                synchronized (s) {
                    s.sendMessage(msg);
                }
            }
            catch (Exception e) {
                log.debug("Edge-A broadcast skip session: {}", e.getMessage());
            }
        }
    }

    /**
     * 将 {@code motion_event} ingest 载荷广播为 §6.2 形态的 {@code type=event} 帧（M2.7 内存 seq）。
     */
    public void publishMotionEvent(Map<String, Object> payload) {
        Object deviceIdObj = payload.get("device_id");
        if (deviceIdObj == null || StringUtils.isBlank(String.valueOf(deviceIdObj))) {
            log.warn("motion_event broadcast skipped: missing device_id");
            return;
        }
        String deviceId = String.valueOf(deviceIdObj).trim();
        long seq = nextSeq(deviceId);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("event_id", UUID.randomUUID().toString());
        inner.put("event_type", "job_status");
        inner.put("device_id", deviceId);
        Object taskId = payload.get("task_id");
        if (taskId != null) {
            inner.put("task_id", String.valueOf(taskId));
        }
        inner.put("ts", System.currentTimeMillis());
        inner.put("seq", seq);

        Map<String, Object> jobPayload = new LinkedHashMap<>();
        if (payload.get("phase") != null) {
            jobPayload.put("phase", String.valueOf(payload.get("phase")));
        }
        if (payload.get("capability") != null) {
            jobPayload.put("capability", String.valueOf(payload.get("capability")));
        }
        if (payload.get("source") != null) {
            jobPayload.put("source", String.valueOf(payload.get("source")));
        }
        if (payload.get("progress") instanceof Map<?, ?> progress) {
            jobPayload.put("progress", progress);
        }
        if (payload.get("tts_hint") instanceof Map<?, ?> ttsHint) {
            jobPayload.put("tts_hint", ttsHint);
        }
        inner.put("payload", jobPayload);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "event");
        envelope.put("event", inner);
        String json = JSONUtil.toJsonStr(envelope);
        String deviceTopic = TOPIC_DEVICE_PREFIX + deviceId;
        remember(deviceTopic, seq, json);
        broadcast(deviceTopic, json);
        if (taskId != null && StringUtils.isNotBlank(String.valueOf(taskId))) {
            String taskTopic = TOPIC_TASK_PREFIX + String.valueOf(taskId).trim();
            remember(taskTopic, seq, json);
            broadcast(taskTopic, json);
        }
    }

    /**
     * 将 {@code device_info} ingest 载荷广播为 Edge-A {@code device_info_reply} 事件（M2.13）。
     */
    public void publishDeviceInfo(Map<String, Object> payload) {
        Object deviceIdObj = payload.get("device_id");
        if (deviceIdObj == null || StringUtils.isBlank(String.valueOf(deviceIdObj))) {
            log.warn("device_info broadcast skipped: missing device_id");
            return;
        }
        Object taskIdObj = payload.get("task_id");
        if (taskIdObj == null || StringUtils.isBlank(String.valueOf(taskIdObj))) {
            log.warn("device_info broadcast skipped: missing task_id");
            return;
        }
        if (payload.get("model") == null
                || payload.get("hw_rev") == null
                || payload.get("fw_rev") == null
                || payload.get("workspace_mm") == null) {
            log.warn("device_info broadcast skipped: missing required device info fields");
            return;
        }

        String deviceId = String.valueOf(deviceIdObj).trim();
        String taskId = String.valueOf(taskIdObj).trim();
        long seq = nextSeq(deviceId);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("event_id", UUID.randomUUID().toString());
        inner.put("event_type", "device_info_reply");
        inner.put("device_id", deviceId);
        inner.put("task_id", taskId);
        inner.put("ts", System.currentTimeMillis());
        inner.put("seq", seq);

        Map<String, Object> infoPayload = new LinkedHashMap<>();
        infoPayload.put("model", String.valueOf(payload.get("model")));
        infoPayload.put("hw_rev", String.valueOf(payload.get("hw_rev")));
        infoPayload.put("fw_rev", String.valueOf(payload.get("fw_rev")));
        infoPayload.put("workspace_mm", payload.get("workspace_mm"));
        if (payload.get("tts_hint") instanceof Map<?, ?> ttsHint) {
            infoPayload.put("tts_hint", ttsHint);
        }
        inner.put("payload", infoPayload);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "event");
        envelope.put("event", inner);
        String json = JSONUtil.toJsonStr(envelope);
        String deviceTopic = TOPIC_DEVICE_PREFIX + deviceId;
        remember(deviceTopic, seq, json);
        broadcast(deviceTopic, json);

        String taskTopic = TOPIC_TASK_PREFIX + taskId;
        remember(taskTopic, seq, json);
        broadcast(taskTopic, json);
    }

    public void publishSelfCheck(Map<String, Object> payload) {
        Object deviceIdObj = payload.get("device_id");
        if (deviceIdObj == null || StringUtils.isBlank(String.valueOf(deviceIdObj))) {
            log.warn("self_check broadcast skipped: missing device_id");
            return;
        }

        String deviceId = String.valueOf(deviceIdObj).trim();
        long seq = nextSeq(deviceId);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("event_id", UUID.randomUUID().toString());
        inner.put("event_type", "self_check");
        inner.put("device_id", deviceId);
        inner.put("ts", System.currentTimeMillis());
        inner.put("seq", seq);

        Map<String, Object> checkPayload = new LinkedHashMap<>();
        if (payload.get("check_id") != null) {
            checkPayload.put("check_id", String.valueOf(payload.get("check_id")));
        }
        if (payload.get("scope") != null) {
            checkPayload.put("scope", String.valueOf(payload.get("scope")));
        }
        if (payload.get("status") != null) {
            checkPayload.put("status", String.valueOf(payload.get("status")));
        }
        if (payload.get("checks") instanceof Map<?, ?> checks) {
            checkPayload.put("checks", checks);
        }
        inner.put("payload", checkPayload);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "event");
        envelope.put("event", inner);
        String json = JSONUtil.toJsonStr(envelope);
        String deviceTopic = TOPIC_DEVICE_PREFIX + deviceId;
        remember(deviceTopic, seq, json);
        broadcast(deviceTopic, json);
    }
}
