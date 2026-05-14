package xiaozhi.modules.appv2.ws;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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

    public static final String TOPIC_DEVICE_PREFIX = "device:";
    public static final String TOPIC_TASK_PREFIX = "task:";

    private final ConcurrentHashMap<String, Set<WebSocketSession>> topicToSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocketSession, Set<String>> rawSessionToTopics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> deviceSeq = new ConcurrentHashMap<>();

    public long nextSeq(String deviceId) {
        return deviceSeq.computeIfAbsent(deviceId, k -> new AtomicLong()).incrementAndGet();
    }

    public void subscribe(WebSocketSession rawSession, WebSocketSession sendSession, String topic) {
        topicToSessions.computeIfAbsent(topic, t -> ConcurrentHashMap.newKeySet()).add(sendSession);
        rawSessionToTopics.computeIfAbsent(rawSession, r -> ConcurrentHashMap.newKeySet()).add(topic);
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
    }

    public void unsubscribeAll(WebSocketSession rawSession, WebSocketSession sendSession) {
        Set<String> topics = rawSessionToTopics.remove(rawSession);
        if (topics == null) {
            return;
        }
        for (String topic : topics) {
            Set<WebSocketSession> set = topicToSessions.get(topic);
            if (set != null) {
                set.remove(sendSession);
            }
        }
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
        inner.put("payload", jobPayload);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "event");
        envelope.put("event", inner);
        String json = JSONUtil.toJsonStr(envelope);

        broadcast(TOPIC_DEVICE_PREFIX + deviceId, json);
        if (taskId != null && StringUtils.isNotBlank(String.valueOf(taskId))) {
            broadcast(TOPIC_TASK_PREFIX + String.valueOf(taskId).trim(), json);
        }
    }
}
