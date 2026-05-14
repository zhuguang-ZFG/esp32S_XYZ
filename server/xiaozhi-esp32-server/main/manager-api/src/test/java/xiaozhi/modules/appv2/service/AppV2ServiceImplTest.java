package xiaozhi.modules.appv2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import xiaozhi.common.utils.Result;
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
    void ingestMotionEventPublishesToEdgeAHub() {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("task_id", "task-1");
        payload.put("phase", "done");
        payload.put("device_id", "dev-1");
        payload.put("capability", "home");

        service.ingestMotionEvent(payload);

        verify(edgeAClientHub).publishMotionEvent(payload);
    }
}
