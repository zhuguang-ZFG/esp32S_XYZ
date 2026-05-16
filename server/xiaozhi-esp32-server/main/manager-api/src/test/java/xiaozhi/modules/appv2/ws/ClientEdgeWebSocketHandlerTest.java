package xiaozhi.modules.appv2.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import cn.hutool.json.JSONUtil;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.appv2.dao.V2AccountDao;
import xiaozhi.modules.appv2.dao.V2DeviceBindingDao;
import xiaozhi.modules.appv2.dao.V2TaskDao;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2TaskEntity;
import xiaozhi.modules.appv2.service.PrimarySessionService;
import xiaozhi.modules.security.service.SysUserTokenService;
import xiaozhi.modules.sys.dto.SysUserDTO;

class ClientEdgeWebSocketHandlerTest {

    @Test
    void subscribeDeviceRequiresAuth() throws Exception {
        ClientEdgeWebSocketHandler handler = newHandler();
        WebSocketSession session = openSession(handler);
        handler.handleTextMessage(session, new TextMessage("{\"op\":\"subscribe_device\",\"device_id\":\"dev-1\"}"));

        verify(session, never()).sendMessage(any(TextMessage.class));
        verify(session).close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "E_AUTH"));
    }

    @Test
    void authRejectsInvalidToken() throws Exception {
        SysUserTokenService sysUserTokenService = mock(SysUserTokenService.class);
        when(sysUserTokenService.getUserByToken("bad-token")).thenThrow(new RenException("bad token"));
        ClientEdgeWebSocketHandler handler = newHandler(sysUserTokenService, mock(V2DeviceBindingDao.class),
                mock(V2TaskDao.class));
        WebSocketSession session = openSession(handler);
        handler.handleTextMessage(session, new TextMessage("{\"op\":\"auth\",\"token\":\"bad-token\"}"));

        verify(session, never()).sendMessage(any(TextMessage.class));
        verify(session).close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "E_AUTH"));
    }

    @Test
    void authRejectsDeletedAccountTombstone() throws Exception {
        SysUserTokenService sysUserTokenService = mock(SysUserTokenService.class);
        SysUserDTO user = new SysUserDTO();
        user.setId(42L);
        when(sysUserTokenService.getUserByToken("token-1")).thenReturn(user);

        V2AccountDao accountDao = mock(V2AccountDao.class);
        V2AccountEntity deleted = new V2AccountEntity();
        deleted.setId(42L);
        deleted.setStatus("deleted");
        when(accountDao.selectById(42L)).thenReturn(deleted);

        ClientEdgeWebSocketHandler handler = newHandler(
                sysUserTokenService,
                accountDao,
                mock(V2DeviceBindingDao.class),
                mock(V2TaskDao.class));
        WebSocketSession session = openSession(handler);
        handler.handleTextMessage(session, new TextMessage("{\"op\":\"auth\",\"token\":\"token-1\"}"));

        verify(session, never()).sendMessage(any(TextMessage.class));
        verify(session).close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "E_AUTH"));
    }

    @Test
    void subscribeDeviceAcknowledgesBoundDeviceAfterAuth() throws Exception {
        SysUserTokenService sysUserTokenService = mock(SysUserTokenService.class);
        SysUserDTO user = new SysUserDTO();
        user.setId(42L);
        when(sysUserTokenService.getUserByToken("token-1")).thenReturn(user);

        V2DeviceBindingDao bindingDao = mock(V2DeviceBindingDao.class);
        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(42L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");
        when(bindingDao.selectOne(any())).thenReturn(binding);

        ClientEdgeWebSocketHandler handler = newHandler(sysUserTokenService, bindingDao, mock(V2TaskDao.class));
        WebSocketSession session = openSession(handler);
        handler.handleTextMessage(session, new TextMessage("{\"op\":\"auth\",\"token\":\"token-1\"}"));
        handler.handleTextMessage(session,
                new TextMessage("{\"op\":\"subscribe_device\",\"device_id\":\"dev-1\",\"since_seq\":7}"));

        TextMessage msg = captureLastTextMessage(session);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = JSONUtil.toBean(msg.getPayload(), Map.class);
        assertEquals("subscribed", payload.get("type"));
        assertEquals("device:dev-1", payload.get("topic"));
        assertEquals(7L, ((Number) payload.get("since_seq")).longValue());
    }

    @Test
    void subscribeDeviceRevalidatesTokenBeforeUsingAuthenticatedSession() throws Exception {
        SysUserTokenService sysUserTokenService = mock(SysUserTokenService.class);
        SysUserDTO user = new SysUserDTO();
        user.setId(42L);
        when(sysUserTokenService.getUserByToken("token-1"))
                .thenReturn(user)
                .thenThrow(new RenException("expired"));

        V2DeviceBindingDao bindingDao = mock(V2DeviceBindingDao.class);
        ClientEdgeWebSocketHandler handler = newHandler(sysUserTokenService, bindingDao, mock(V2TaskDao.class));
        WebSocketSession session = openSession(handler);
        handler.handleTextMessage(session, new TextMessage("{\"op\":\"auth\",\"token\":\"token-1\"}"));
        handler.handleTextMessage(session,
                new TextMessage("{\"op\":\"subscribe_device\",\"device_id\":\"dev-1\",\"since_seq\":7}"));

        verify(session).close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "E_AUTH"));
        verify(bindingDao, never()).selectOne(any());
    }

    @Test
    void ackReturnsMonotonicSequenceForSubscribedTopic() throws Exception {
        SysUserTokenService sysUserTokenService = mock(SysUserTokenService.class);
        SysUserDTO user = new SysUserDTO();
        user.setId(42L);
        when(sysUserTokenService.getUserByToken("token-1")).thenReturn(user);

        V2DeviceBindingDao bindingDao = mock(V2DeviceBindingDao.class);
        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(42L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");
        when(bindingDao.selectOne(any())).thenReturn(binding);

        ClientEdgeWebSocketHandler handler = newHandler(sysUserTokenService, bindingDao, mock(V2TaskDao.class));
        WebSocketSession session = openSession(handler);
        handler.handleTextMessage(session, new TextMessage("{\"op\":\"auth\",\"token\":\"token-1\"}"));
        handler.handleTextMessage(session, new TextMessage("{\"op\":\"subscribe_device\",\"device_id\":\"dev-1\"}"));
        handler.handleTextMessage(session, new TextMessage("{\"op\":\"ack\",\"topic\":\"device:dev-1\",\"seq\":5}"));
        handler.handleTextMessage(session, new TextMessage("{\"op\":\"ack\",\"topic\":\"device:dev-1\",\"seq\":3}"));

        TextMessage msg = captureLastTextMessage(session);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = JSONUtil.toBean(msg.getPayload(), Map.class);
        assertEquals("acked", payload.get("type"));
        assertEquals("device:dev-1", payload.get("topic"));
        assertEquals(5L, ((Number) payload.get("seq")).longValue());
    }

    @Test
    void subscribeTaskRejectsTaskOwnedByAnotherUser() throws Exception {
        SysUserTokenService sysUserTokenService = mock(SysUserTokenService.class);
        SysUserDTO user = new SysUserDTO();
        user.setId(42L);
        when(sysUserTokenService.getUserByToken("token-1")).thenReturn(user);

        V2TaskDao taskDao = mock(V2TaskDao.class);
        V2TaskEntity task = new V2TaskEntity();
        task.setId("task-1");
        task.setAccountId(99L);
        when(taskDao.selectById("task-1")).thenReturn(task);

        ClientEdgeWebSocketHandler handler = newHandler(sysUserTokenService, mock(V2DeviceBindingDao.class), taskDao);
        WebSocketSession session = openSession(handler);
        handler.handleTextMessage(session, new TextMessage("{\"op\":\"auth\",\"token\":\"token-1\"}"));
        handler.handleTextMessage(session, new TextMessage("{\"op\":\"subscribe_task\",\"task_id\":\"task-1\"}"));

        TextMessage msg = captureLastTextMessage(session);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = JSONUtil.toBean(msg.getPayload(), Map.class);
        assertEquals("error", payload.get("type"));
        assertEquals("E_FORBIDDEN", payload.get("code"));
        assertEquals("task not found", payload.get("message"));
    }

    @Test
    void claimPrimaryDelegatesAndAcknowledgesCurrentSession() throws Exception {
        SysUserTokenService sysUserTokenService = mock(SysUserTokenService.class);
        SysUserDTO user = new SysUserDTO();
        user.setId(42L);
        when(sysUserTokenService.getUserByToken("token-1")).thenReturn(user);

        PrimarySessionService primarySessionService = mock(PrimarySessionService.class);
        ClientEdgeWebSocketHandler handler = newHandler(sysUserTokenService, mock(V2DeviceBindingDao.class),
                mock(V2TaskDao.class), primarySessionService);
        WebSocketSession session = openSession(handler);
        handler.handleTextMessage(session, new TextMessage("{\"op\":\"auth\",\"token\":\"token-1\"}"));
        handler.handleTextMessage(session, new TextMessage("{\"op\":\"claim_primary\",\"device_id\":\"dev-1\"}"));

        verify(primarySessionService).claimPrimary(42L, "dev-1", "token-1");
        TextMessage msg = captureLastTextMessage(session);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = JSONUtil.toBean(msg.getPayload(), Map.class);
        assertEquals("primary_claimed", payload.get("type"));
        assertEquals("dev-1", payload.get("device_id"));
        assertEquals("primary", payload.get("role"));
    }

    private static ClientEdgeWebSocketHandler newHandler() {
        return newHandler(mock(SysUserTokenService.class), mock(V2DeviceBindingDao.class), mock(V2TaskDao.class));
    }

    private static ClientEdgeWebSocketHandler newHandler(SysUserTokenService sysUserTokenService,
            V2DeviceBindingDao bindingDao, V2TaskDao taskDao) {
        return newHandler(sysUserTokenService, mock(V2AccountDao.class), bindingDao, taskDao);
    }

    private static ClientEdgeWebSocketHandler newHandler(SysUserTokenService sysUserTokenService,
            V2AccountDao accountDao, V2DeviceBindingDao bindingDao, V2TaskDao taskDao) {
        return newHandler(sysUserTokenService, accountDao, bindingDao, taskDao, mock(PrimarySessionService.class));
    }

    private static ClientEdgeWebSocketHandler newHandler(SysUserTokenService sysUserTokenService,
            V2DeviceBindingDao bindingDao, V2TaskDao taskDao, PrimarySessionService primarySessionService) {
        return newHandler(sysUserTokenService, mock(V2AccountDao.class), bindingDao, taskDao, primarySessionService);
    }

    private static ClientEdgeWebSocketHandler newHandler(SysUserTokenService sysUserTokenService,
            V2AccountDao accountDao, V2DeviceBindingDao bindingDao, V2TaskDao taskDao,
            PrimarySessionService primarySessionService) {
        return new ClientEdgeWebSocketHandler(new EdgeAClientHub(), sysUserTokenService, accountDao, bindingDao,
                taskDao, primarySessionService);
    }

    private static WebSocketSession session() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private static WebSocketSession openSession(ClientEdgeWebSocketHandler handler) throws Exception {
        WebSocketSession session = session();
        handler.afterConnectionEstablished(session);
        session.getAttributes().put("edgeA.sendSession", session);
        return session;
    }

    private static TextMessage captureLastTextMessage(WebSocketSession session) throws Exception {
        org.mockito.ArgumentCaptor<TextMessage> captor = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(session, org.mockito.Mockito.atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues().get(captor.getAllValues().size() - 1);
    }
}
