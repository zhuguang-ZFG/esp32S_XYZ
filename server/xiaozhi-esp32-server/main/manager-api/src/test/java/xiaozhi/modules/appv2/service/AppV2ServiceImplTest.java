package xiaozhi.modules.appv2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.common.page.TokenDTO;
import xiaozhi.common.user.UserDetail;
import xiaozhi.common.utils.MessageUtils;
import xiaozhi.common.utils.Result;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.appv2.dao.V2AccountDao;
import xiaozhi.modules.appv2.dao.V2ActivationCodeDao;
import xiaozhi.modules.appv2.dao.V2DeviceBindingDao;
import xiaozhi.modules.appv2.dao.V2DeviceDao;
import xiaozhi.modules.appv2.dao.V2TaskDao;
import xiaozhi.modules.appv2.dto.V2BindDeviceRequest;
import xiaozhi.modules.appv2.dto.V2BindDeviceResponse;
import xiaozhi.modules.appv2.dto.V2LoginRequest;
import xiaozhi.modules.appv2.dto.V2LoginResponse;
import xiaozhi.modules.appv2.dto.V2SubmitTaskRequest;
import xiaozhi.modules.appv2.dto.V2SubmitTaskResponse;
import xiaozhi.modules.appv2.entity.V2ActivationCodeEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2DeviceEntity;
import xiaozhi.modules.appv2.entity.V2TaskEntity;
import xiaozhi.modules.appv2.service.DeviceServerMotionGateway;
import xiaozhi.modules.appv2.service.WechatLoginGateway.WechatSession;
import xiaozhi.modules.appv2.service.impl.AppV2ServiceImpl;
import xiaozhi.modules.appv2.ws.EdgeAClientHub;
import xiaozhi.modules.security.service.SysUserTokenService;
import xiaozhi.modules.security.user.SecurityUser;
import xiaozhi.modules.sys.dto.SysUserDTO;
import xiaozhi.modules.sys.service.SysUserService;

@ExtendWith(MockitoExtension.class)
class AppV2ServiceImplTest {
    @Mock
    private V2AccountDao v2AccountDao;
    @Mock
    private V2DeviceDao v2DeviceDao;
    @Mock
    private V2DeviceBindingDao v2DeviceBindingDao;
    @Mock
    private V2ActivationCodeDao v2ActivationCodeDao;
    @Mock
    private V2TaskDao v2TaskDao;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private SysUserTokenService sysUserTokenService;
    @Mock
    private WechatLoginGateway wechatLoginGateway;
    @Mock
    private DeviceServerMotionGateway deviceServerMotionGateway;
    @Mock
    private EdgeAClientHub edgeAClientHub;

    @InjectMocks
    private AppV2ServiceImpl service;

    @Test
    void loginCreatesTokenForUnionidUser() {
        V2LoginRequest request = new V2LoginRequest();
        request.setCode("wx-code-1");
        request.setDisplayName("tester");

        when(wechatLoginGateway.exchange("wx-code-1"))
                .thenReturn(new WechatSession("u-001", "o-001"));

        SysUserDTO user = new SysUserDTO();
        user.setId(11L);
        user.setUsername("wx:u-001");
        when(sysUserService.getByUsername("wx:u-001")).thenReturn(null, user);

        TokenDTO tokenDTO = new TokenDTO();
        tokenDTO.setToken("token-1");
        tokenDTO.setExpire(3600);
        Result<TokenDTO> tokenResult = new Result<TokenDTO>().ok(tokenDTO);
        when(sysUserTokenService.createToken(11L)).thenReturn(tokenResult);

        V2LoginResponse response = service.login(request);

        assertEquals(11L, response.getAccountId());
        assertEquals("token-1", response.getToken());
        verify(sysUserService).save(any(SysUserDTO.class));
        verify(v2AccountDao).insert(any());
    }

    @Test
    void bindDeviceUsesActiveUserAndActivationCode() {
        V2BindDeviceRequest request = new V2BindDeviceRequest();
        request.setDeviceSn("SN-001");
        request.setActivationCode("ACT-001");

        V2ActivationCodeEntity activation = new V2ActivationCodeEntity();
        activation.setDeviceSn("SN-001");
        activation.setActivationCode("ACT-001");
        activation.setStatus("provisioned");
        when(v2ActivationCodeDao.selectOne(any())).thenReturn(activation);

        UserDetail user = new UserDetail();
        user.setId(21L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            V2BindDeviceResponse response = service.bindDevice(request);

            assertEquals(21L, response.getAccountId());
            assertEquals("active", response.getBindingStatus());
            verify(v2DeviceDao).insert(any());
            verify(v2DeviceBindingDao).insert(any());
        }
    }

    @Test
    void bindDeviceIsIdempotentForExistingActiveOwner() {
        V2BindDeviceRequest request = new V2BindDeviceRequest();
        request.setDeviceSn(" SN-001 ");
        request.setActivationCode(" ACT-001 ");

        V2ActivationCodeEntity activation = new V2ActivationCodeEntity();
        activation.setDeviceSn("SN-001");
        activation.setActivationCode("ACT-001");
        activation.setDeviceId("dev_SN_001");
        activation.setStatus("bound");
        activation.setUsedAt(new java.util.Date());
        when(v2ActivationCodeDao.selectOne(any())).thenReturn(activation);

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(21L);
        binding.setDeviceId("dev_SN_001");
        binding.setBindingStatus("active");
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);

        V2DeviceEntity device = new V2DeviceEntity();
        device.setId("dev_SN_001");
        device.setDeviceSn("SN-001");
        device.setStatus("bound");
        when(v2DeviceDao.selectById("dev_SN_001")).thenReturn(device);

        UserDetail user = new UserDetail();
        user.setId(21L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            V2BindDeviceResponse response = service.bindDevice(request);

            assertEquals(21L, response.getAccountId());
            assertEquals("dev_SN_001", response.getDeviceId());
            verify(v2DeviceBindingDao, never()).insert(any());
            verify(v2ActivationCodeDao).updateById(activation);
        }
    }

    @Test
    void bindDeviceRejectsBindingOwnedByAnotherUser() {
        V2BindDeviceRequest request = new V2BindDeviceRequest();
        request.setDeviceSn("SN-001");
        request.setActivationCode("ACT-001");

        V2ActivationCodeEntity activation = new V2ActivationCodeEntity();
        activation.setDeviceSn("SN-001");
        activation.setActivationCode("ACT-001");
        activation.setDeviceId("dev_SN_001");
        activation.setStatus("provisioned");
        when(v2ActivationCodeDao.selectOne(any())).thenReturn(activation);

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(99L);
        binding.setDeviceId("dev_SN_001");
        binding.setBindingStatus("active");
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);

        UserDetail user = new UserDetail();
        user.setId(21L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);
            try (MockedStatic<MessageUtils> mockedMessageUtils = mockStatic(MessageUtils.class)) {
                mockedMessageUtils.when(() -> MessageUtils.getMessage(org.mockito.ArgumentMatchers.anyInt()))
                        .thenReturn("device already activated");
                RenException ex = assertThrows(RenException.class, () -> service.bindDevice(request));
                assertEquals("device already activated", ex.getMsg());
                verify(v2DeviceBindingDao, never()).insert(any());
            }
        }
    }

    @Test
    void submitTaskReturnsExistingTaskForSameRequestId() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("home");
        request.setRequestId("req-1");

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");

        V2TaskEntity existing = new V2TaskEntity();
        existing.setId("task-123");
        existing.setStatus("accepted");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        when(v2TaskDao.selectOne(any())).thenReturn(existing);

        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            V2SubmitTaskResponse response = service.submitTask("dev-1", request);

            assertEquals("task-123", response.getTaskId());
            assertEquals("accepted", response.getStatus());
            verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
        }
    }

    @Test
    void submitTaskForwardsToDeviceServerForNewTask() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("home");

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            V2SubmitTaskResponse response = service.submitTask("dev-1", request);

            assertEquals("accepted", response.getStatus());
            verify(v2TaskDao).insert(any(V2TaskEntity.class));
            verify(deviceServerMotionGateway).forwardAcceptedTask(eq("dev-1"), any(V2TaskEntity.class), eq(request));
        }
    }

    @Test
    void submitTaskNormalizesIdentifiersBeforePersisting() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability(" home ");
        request.setRequestId(" req-1 ");
        request.setTraceId(" trace-1 ");
        request.setSource(" app ");

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            service.submitTask(" dev-1 ", request);

            org.mockito.ArgumentCaptor<V2TaskEntity> captor = org.mockito.ArgumentCaptor.forClass(V2TaskEntity.class);
            verify(v2TaskDao).insert(captor.capture());
            V2TaskEntity inserted = captor.getValue();
            assertEquals("dev-1", inserted.getDeviceId());
            assertEquals("req-1", inserted.getRequestId());
            assertEquals("trace-1", inserted.getTraceId());
            assertEquals("home", inserted.getCapability());
            assertEquals("app", inserted.getSource());
        }
    }

    @Test
    void ingestMotionEventPublishesToEdgeAHub() {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("task_id", "task-1");
        payload.put("phase", "done");
        payload.put("device_id", "dev-1");
        payload.put("capability", "home");
        V2TaskEntity task = new V2TaskEntity();
        task.setId("task-1");
        task.setStatus("accepted");
        when(v2TaskDao.selectById("task-1")).thenReturn(task);

        service.ingestMotionEvent(payload);

        verify(v2TaskDao).updateById(any(V2TaskEntity.class));
        verify(edgeAClientHub).publishMotionEvent(payload);
    }

    @Test
    void ingestMotionEventUpdatesTaskStateAndResult() {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("task_id", "task-2");
        payload.put("phase", "running");
        payload.put("device_id", "dev-2");
        payload.put("capability", "move");
        payload.put("started_at", 1_715_000_000L);
        payload.put("result", java.util.Map.of("progress", 50));

        V2TaskEntity task = new V2TaskEntity();
        task.setId("task-2");
        task.setStatus("accepted");
        when(v2TaskDao.selectById("task-2")).thenReturn(task);

        service.ingestMotionEvent(payload);

        org.mockito.ArgumentCaptor<V2TaskEntity> captor = org.mockito.ArgumentCaptor.forClass(V2TaskEntity.class);
        verify(v2TaskDao).updateById(captor.capture());
        V2TaskEntity updated = captor.getValue();
        assertEquals("running", updated.getStatus());
        assertEquals("{\"progress\":50}", updated.getResultJson());
        assertEquals(1_715_000_000_000L, updated.getStartedAt().getTime());
        assertNull(updated.getFinishedAt());
    }

    @Test
    void ingestMotionEventMarksTaskFailedWithTerminalMetadata() {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("task_id", "task-3");
        payload.put("phase", "error");
        payload.put("device_id", "dev-3");
        payload.put("error_code", "LIMIT");
        payload.put("error_message", "out of range");
        payload.put("finished_at", "2026-05-14T03:00:00Z");

        V2TaskEntity task = new V2TaskEntity();
        task.setId("task-3");
        task.setStatus("running");
        when(v2TaskDao.selectById("task-3")).thenReturn(task);

        service.ingestMotionEvent(payload);

        org.mockito.ArgumentCaptor<V2TaskEntity> captor = org.mockito.ArgumentCaptor.forClass(V2TaskEntity.class);
        verify(v2TaskDao).updateById(captor.capture());
        V2TaskEntity updated = captor.getValue();
        assertEquals("failed", updated.getStatus());
        assertEquals("LIMIT", updated.getErrorCode());
        assertEquals("out of range", updated.getErrorMessage());
        assertEquals(1_778_727_600_000L, updated.getFinishedAt().getTime());
    }
}
