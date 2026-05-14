package xiaozhi.modules.appv2.ws;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.appv2.dao.V2DeviceBindingDao;
import xiaozhi.modules.appv2.dao.V2TaskDao;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2TaskEntity;
import xiaozhi.modules.security.service.SysUserTokenService;
import xiaozhi.modules.sys.dto.SysUserDTO;

/**
 * Edge-A：{@code /ws/v1/client} 文本帧（§9.2 + 首帧 auth，见 {@code docs/schemas/edge_a}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientEdgeWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_USER_ID = "edgeA.userId";
    private static final String ATTR_SEND_SESSION = "edgeA.sendSession";
    private static final String BINDING_STATUS_ACTIVE = "active";

    private static final int SEND_BUFFER_LIMIT = 512 * 1024;
    private static final int SEND_TIME_LIMIT_MS = 60_000;

    private final EdgeAClientHub edgeAClientHub;
    private final SysUserTokenService sysUserTokenService;
    private final V2DeviceBindingDao v2DeviceBindingDao;
    private final V2TaskDao v2TaskDao;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession wrapped = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT);
        session.getAttributes().put(ATTR_SEND_SESSION, wrapped);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WebSocketSession sendSession = sendSession(session);
        String text = message.getPayload();
        if (StringUtils.isBlank(text)) {
            return;
        }
        final JSONObject root;
        try {
            root = JSONUtil.parseObj(text);
        }
        catch (Exception e) {
            sendError(sendSession, "E_BAD_REQUEST", "invalid json");
            return;
        }
        String op = root.getStr("op");
        if (StringUtils.isBlank(op)) {
            sendError(sendSession, "E_BAD_REQUEST", "missing op");
            return;
        }

        Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
        if (!"auth".equals(op) && userId == null) {
            sendError(sendSession, "E_AUTH", "auth required");
            session.close(CloseStatus.POLICY_VIOLATION.withReason("E_AUTH"));
            return;
        }

        switch (op) {
            case "auth" -> handleAuth(session, sendSession, root);
            case "subscribe_device" -> handleSubscribeDevice(session, sendSession, userId, root);
            case "subscribe_task" -> handleSubscribeTask(session, sendSession, userId, root);
            case "unsubscribe" -> handleUnsubscribe(session, sendSession, root);
            case "ping" -> sendSession.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
            case "ack" -> { /* M2.7 占位 */ }
            default -> sendError(sendSession, "E_BAD_REQUEST", "unknown op: " + op);
        }
    }

    private void handleAuth(WebSocketSession raw, WebSocketSession sendSession, JSONObject root) throws IOException {
        String token = root.getStr("token");
        if (StringUtils.isBlank(token)) {
            sendError(sendSession, "E_AUTH", "missing token");
            raw.close(CloseStatus.POLICY_VIOLATION.withReason("E_AUTH"));
            return;
        }
        try {
            SysUserDTO user = sysUserTokenService.getUserByToken(token.trim());
            raw.getAttributes().put(ATTR_USER_ID, user.getId());
            log.debug("Edge-A auth ok userId={}", user.getId());
        }
        catch (RenException e) {
            sendError(sendSession, "E_AUTH", e.getMsg() != null ? e.getMsg() : "invalid token");
            raw.close(CloseStatus.POLICY_VIOLATION.withReason("E_AUTH"));
        }
    }

    private void handleSubscribeDevice(WebSocketSession raw, WebSocketSession sendSession, Long userId, JSONObject root)
            throws IOException {
        String deviceId = root.getStr("device_id");
        if (StringUtils.isBlank(deviceId)) {
            sendError(sendSession, "E_BAD_REQUEST", "missing device_id");
            return;
        }
        V2DeviceBindingEntity binding = v2DeviceBindingDao.selectOne(new LambdaQueryWrapper<V2DeviceBindingEntity>()
                .eq(V2DeviceBindingEntity::getAccountId, userId)
                .eq(V2DeviceBindingEntity::getDeviceId, deviceId.trim())
                .eq(V2DeviceBindingEntity::getBindingStatus, BINDING_STATUS_ACTIVE)
                .last("limit 1"));
        if (binding == null) {
            sendError(sendSession, "E_FORBIDDEN", "no active binding for device");
            return;
        }
        String topic = EdgeAClientHub.TOPIC_DEVICE_PREFIX + deviceId.trim();
        edgeAClientHub.subscribe(raw, sendSession, topic);
        long since = root.getLong("since_seq", 0L);
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "subscribed");
        ack.put("topic", topic);
        ack.put("since_seq", since);
        sendSession.sendMessage(new TextMessage(JSONUtil.toJsonStr(ack)));
    }

    private void handleSubscribeTask(WebSocketSession raw, WebSocketSession sendSession, Long userId, JSONObject root)
            throws IOException {
        String taskId = root.getStr("task_id");
        if (StringUtils.isBlank(taskId)) {
            sendError(sendSession, "E_BAD_REQUEST", "missing task_id");
            return;
        }
        V2TaskEntity task = v2TaskDao.selectById(taskId.trim());
        if (task == null || !Objects.equals(task.getAccountId(), userId)) {
            sendError(sendSession, "E_FORBIDDEN", "task not found");
            return;
        }
        String topic = EdgeAClientHub.TOPIC_TASK_PREFIX + taskId.trim();
        edgeAClientHub.subscribe(raw, sendSession, topic);
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "subscribed");
        ack.put("topic", topic);
        ack.put("since_seq", 0L);
        sendSession.sendMessage(new TextMessage(JSONUtil.toJsonStr(ack)));
    }

    private void handleUnsubscribe(WebSocketSession raw, WebSocketSession sendSession, JSONObject root) {
        String topic = root.getStr("topic");
        if (StringUtils.isBlank(topic)) {
            return;
        }
        edgeAClientHub.unsubscribe(raw, sendSession, topic.trim());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        WebSocketSession sendSession = sendSession(session);
        edgeAClientHub.unsubscribeAll(session, sendSession);
        session.getAttributes().remove(ATTR_USER_ID);
        session.getAttributes().remove(ATTR_SEND_SESSION);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Edge-A transport error: {}", exception.getMessage());
        WebSocketSession sendSession = sendSession(session);
        edgeAClientHub.unsubscribeAll(session, sendSession);
    }

    private static WebSocketSession sendSession(WebSocketSession session) {
        Object w = session.getAttributes().get(ATTR_SEND_SESSION);
        return w instanceof WebSocketSession ws ? ws : session;
    }

    private static void sendError(WebSocketSession sendSession, String code, String message) throws IOException {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("type", "error");
        err.put("code", code);
        err.put("message", message);
        sendSession.sendMessage(new TextMessage(JSONUtil.toJsonStr(err)));
    }
}
