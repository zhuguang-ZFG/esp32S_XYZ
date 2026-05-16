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
import xiaozhi.modules.appv2.dao.V2AccountDao;
import xiaozhi.modules.appv2.dao.V2DeviceBindingDao;
import xiaozhi.modules.appv2.dao.V2TaskDao;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2TaskEntity;
import xiaozhi.modules.appv2.service.PrimarySessionException;
import xiaozhi.modules.appv2.service.PrimarySessionService;
import xiaozhi.modules.security.service.SysUserTokenService;
import xiaozhi.modules.sys.dto.SysUserDTO;

/**
 * Edge-A websocket endpoint for client event subscriptions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientEdgeWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_USER_ID = "edgeA.userId";
    private static final String ATTR_SESSION_ID = "edgeA.sessionId";
    private static final String ATTR_SEND_SESSION = "edgeA.sendSession";
    private static final String BINDING_STATUS_ACTIVE = "active";
    private static final String ACCOUNT_STATUS_DELETED = "deleted";

    private static final int SEND_BUFFER_LIMIT = 512 * 1024;
    private static final int SEND_TIME_LIMIT_MS = 60_000;

    private final EdgeAClientHub edgeAClientHub;
    private final SysUserTokenService sysUserTokenService;
    private final V2AccountDao v2AccountDao;
    private final V2DeviceBindingDao v2DeviceBindingDao;
    private final V2TaskDao v2TaskDao;
    private final PrimarySessionService primarySessionService;

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
        } catch (Exception e) {
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
            session.close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "E_AUTH"));
            return;
        }
        if (!"auth".equals(op) && !revalidateAuthenticatedSession(session, userId)) {
            return;
        }

        switch (op) {
            case "auth" -> handleAuth(session, sendSession, root);
            case "subscribe_device" -> handleSubscribeDevice(session, sendSession, userId, root);
            case "subscribe_task" -> handleSubscribeTask(session, sendSession, userId, root);
            case "unsubscribe" -> handleUnsubscribe(session, sendSession, root);
            case "claim_primary" -> handleClaimPrimary(session, sendSession, userId, root);
            case "ping" -> sendSession.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
            case "ack" -> handleAck(session, sendSession, root);
            default -> sendError(sendSession, "E_BAD_REQUEST", "unknown op: " + op);
        }
    }

    private boolean revalidateAuthenticatedSession(WebSocketSession raw, Long userId) throws IOException {
        String sessionId = (String) raw.getAttributes().get(ATTR_SESSION_ID);
        if (StringUtils.isBlank(sessionId)) {
            raw.close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "E_AUTH"));
            return false;
        }
        try {
            SysUserDTO user = sysUserTokenService.getUserByToken(sessionId);
            if (user == null || !Objects.equals(user.getId(), userId) || isDeletedAccount(userId)) {
                raw.close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "E_AUTH"));
                return false;
            }
            return true;
        } catch (RenException e) {
            raw.close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "E_AUTH"));
            return false;
        }
    }

    private boolean isDeletedAccount(Long userId) {
        V2AccountEntity account = v2AccountDao.selectById(userId);
        return account != null && ACCOUNT_STATUS_DELETED.equalsIgnoreCase(account.getStatus());
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
            if (isDeletedAccount(user.getId())) {
                raw.close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "E_AUTH"));
                return;
            }
            raw.getAttributes().put(ATTR_USER_ID, user.getId());
            raw.getAttributes().put(ATTR_SESSION_ID, token.trim());
            Map<String, Object> ack = new LinkedHashMap<>();
            ack.put("type", "authed");
            ack.put("user_id", user.getId());
            sendSession.sendMessage(new TextMessage(JSONUtil.toJsonStr(ack)));
            log.debug("Edge-A auth ok userId={}", user.getId());
        } catch (RenException e) {
            raw.close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "E_AUTH"));
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
                .orderByDesc(V2DeviceBindingEntity::getUpdatedAt)
                .orderByDesc(V2DeviceBindingEntity::getId)
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
        edgeAClientHub.replaySince(sendSession, topic, since);
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
        long since = root.getLong("since_seq", 0L);
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "subscribed");
        ack.put("topic", topic);
        ack.put("since_seq", since);
        sendSession.sendMessage(new TextMessage(JSONUtil.toJsonStr(ack)));
        edgeAClientHub.replaySince(sendSession, topic, since);
    }

    private void handleUnsubscribe(WebSocketSession raw, WebSocketSession sendSession, JSONObject root) throws IOException {
        String topic = root.getStr("topic");
        if (StringUtils.isBlank(topic)) {
            sendError(sendSession, "E_BAD_REQUEST", "missing topic");
            return;
        }
        String normalizedTopic = topic.trim();
        edgeAClientHub.unsubscribe(raw, sendSession, normalizedTopic);
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "unsubscribed");
        ack.put("topic", normalizedTopic);
        sendSession.sendMessage(new TextMessage(JSONUtil.toJsonStr(ack)));
    }

    private void handleClaimPrimary(WebSocketSession raw, WebSocketSession sendSession, Long userId, JSONObject root)
            throws IOException {
        String deviceId = root.getStr("device_id");
        if (StringUtils.isBlank(deviceId)) {
            sendError(sendSession, "E_BAD_REQUEST", "missing device_id");
            return;
        }
        String sessionId = (String) raw.getAttributes().get(ATTR_SESSION_ID);
        try {
            primarySessionService.claimPrimary(userId, deviceId.trim(), sessionId);
        } catch (PrimarySessionException e) {
            sendError(sendSession, e.getCode(), e.getMessage());
            return;
        } catch (RenException e) {
            sendError(sendSession, "E_FORBIDDEN", e.getMsg());
            return;
        }
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "primary_claimed");
        ack.put("device_id", deviceId.trim());
        ack.put("role", "primary");
        sendSession.sendMessage(new TextMessage(JSONUtil.toJsonStr(ack)));
    }

    private void handleAck(WebSocketSession raw, WebSocketSession sendSession, JSONObject root) throws IOException {
        String topic = root.getStr("topic");
        Long seq = root.getLong("seq");
        if (StringUtils.isBlank(topic)) {
            sendError(sendSession, "E_BAD_REQUEST", "missing topic");
            return;
        }
        if (seq == null || seq.longValue() < 0L) {
            sendError(sendSession, "E_BAD_REQUEST", "missing seq");
            return;
        }
        String normalizedTopic = topic.trim();
        if (!edgeAClientHub.isSubscribed(raw, normalizedTopic)) {
            sendError(sendSession, "E_FORBIDDEN", "topic not subscribed");
            return;
        }
        long ackedSeq = edgeAClientHub.ack(raw, normalizedTopic, seq.longValue());
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "acked");
        ack.put("topic", normalizedTopic);
        ack.put("seq", ackedSeq);
        sendSession.sendMessage(new TextMessage(JSONUtil.toJsonStr(ack)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        WebSocketSession sendSession = sendSession(session);
        edgeAClientHub.unsubscribeAll(session, sendSession);
        session.getAttributes().remove(ATTR_USER_ID);
        session.getAttributes().remove(ATTR_SESSION_ID);
        session.getAttributes().remove(ATTR_SEND_SESSION);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Edge-A transport error: {}", exception.getMessage());
        WebSocketSession sendSession = sendSession(session);
        edgeAClientHub.unsubscribeAll(session, sendSession);
        try {
            session.close(CloseStatus.SERVER_ERROR.withReason("transport error"));
        } catch (IOException ignored) {
            // already broken
        }
    }

    private static WebSocketSession sendSession(WebSocketSession session) {
        Object wrapped = session.getAttributes().get(ATTR_SEND_SESSION);
        return wrapped instanceof WebSocketSession ws ? ws : session;
    }

    private static void sendError(WebSocketSession sendSession, String code, String message) throws IOException {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("type", "error");
        err.put("code", code);
        err.put("message", message);
        sendSession.sendMessage(new TextMessage(JSONUtil.toJsonStr(err)));
    }
}
