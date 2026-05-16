package xiaozhi.modules.appv2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.common.page.TokenDTO;
import xiaozhi.common.user.UserDetail;
import xiaozhi.common.utils.MessageUtils;
import xiaozhi.common.utils.Result;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.appv2.dao.V2AccountDao;
import xiaozhi.modules.appv2.dao.V2ActivationCodeDao;
import xiaozhi.modules.appv2.dao.V2DeviceBindingDao;
import xiaozhi.modules.appv2.dao.V2DeviceDao;
import xiaozhi.modules.appv2.dao.V2DeviceSelfCheckEventDao;
import xiaozhi.modules.appv2.dao.V2TaskDao;
import xiaozhi.modules.appv2.dto.V2BindDeviceRequest;
import xiaozhi.modules.appv2.dto.V2BindDeviceResponse;
import xiaozhi.modules.appv2.dto.V2LoginRequest;
import xiaozhi.modules.appv2.dto.V2LoginResponse;
import xiaozhi.modules.appv2.dto.V2SubmitTaskRequest;
import xiaozhi.modules.appv2.dto.V2SubmitTaskResponse;
import xiaozhi.modules.appv2.dto.V2TaskApprovalRequest;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2ActivationCodeEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2DeviceEntity;
import xiaozhi.modules.appv2.entity.V2DeviceSelfCheckEventEntity;
import xiaozhi.modules.appv2.entity.V2TaskEntity;
import xiaozhi.modules.appv2.service.DeviceServerMotionGateway;
import xiaozhi.modules.appv2.service.DeviceSupplyService;
import xiaozhi.modules.appv2.service.WechatLoginGateway.WechatSession;
import xiaozhi.modules.appv2.service.impl.AppV2ServiceImpl;
import xiaozhi.modules.appv2.service.contentaudit.ContentAuditException;
import xiaozhi.modules.appv2.service.contentaudit.ContentAuditLogService;
import xiaozhi.modules.appv2.service.contentaudit.ContentAuditService;
import xiaozhi.modules.appv2.service.graphic.DrawingValidationException;
import xiaozhi.modules.appv2.service.graphic.SingleLineSvgValidator;
import xiaozhi.modules.appv2.service.projection.DrawGeneratedProjectionService;
import xiaozhi.modules.appv2.service.projection.WriteTextProjectionService;
import xiaozhi.modules.appv2.service.resource.EntitlementValidationException;
import xiaozhi.modules.appv2.service.resource.FactoryEntitlementService;
import xiaozhi.modules.appv2.service.resource.ResourceEntitlementService;
import xiaozhi.modules.appv2.service.safety.SafetyAuditService;
import xiaozhi.modules.appv2.service.safety.SafetyValidationException;
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
    private V2DeviceSelfCheckEventDao v2DeviceSelfCheckEventDao;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private SysUserTokenService sysUserTokenService;
    @Mock
    private WechatLoginGateway wechatLoginGateway;
    @Mock
    private DeviceServerMotionGateway deviceServerMotionGateway;
    @Mock
    private DeviceSupplyService deviceSupplyService;
    @Mock
    private PrimarySessionService primarySessionService;
    @Mock
    private ProductNotificationOutboxService productNotificationOutboxService;
    @Mock
    private EdgeAClientHub edgeAClientHub;
    @Spy
    private WriteTextProjectionService writeTextProjectionService = new WriteTextProjectionService();
    @Spy
    private ContentAuditService contentAuditService = new ContentAuditService();
    @Mock
    private ContentAuditLogService contentAuditLogService;
    @Spy
    private SingleLineSvgValidator singleLineSvgValidator = new SingleLineSvgValidator();
    @Spy
    private DrawGeneratedProjectionService drawGeneratedProjectionService =
            new DrawGeneratedProjectionService(new SingleLineSvgValidator());
    @Mock
    private SafetyAuditService safetyAuditService;
    @Mock
    private FactoryEntitlementService factoryEntitlementService;
    @Mock
    private ResourceEntitlementService resourceEntitlementService;

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
    void loginRejectsDeletedAccountTombstone() {
        V2LoginRequest request = new V2LoginRequest();
        request.setCode("wx-code-deleted");
        request.setDisplayName("tester");

        when(wechatLoginGateway.exchange("wx-code-deleted"))
                .thenReturn(new WechatSession("u-deleted", "o-deleted"));

        V2AccountEntity deleted = new V2AccountEntity();
        deleted.setId(12L);
        deleted.setUnionid("u-deleted");
        deleted.setStatus("deleted");
        when(v2AccountDao.selectOne(any())).thenReturn(deleted);

        try (MockedStatic<MessageUtils> mockedMessageUtils = mockStatic(MessageUtils.class)) {
            mockedMessageUtils.when(() -> MessageUtils.getMessage(ErrorCode.ACCOUNT_DISABLE))
                    .thenReturn("account disabled");

            RenException ex = assertThrows(RenException.class, () -> service.login(request));

            assertEquals(ErrorCode.ACCOUNT_DISABLE, ex.getCode());
            verify(sysUserService, never()).getByUsername(any());
            verify(sysUserService, never()).save(any());
            verify(sysUserTokenService, never()).createToken(any());
            verify(v2AccountDao, never()).insert(any());
            verify(v2AccountDao, never()).updateById(any());
        }
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
            verify(factoryEntitlementService).ensureFactoryEntitlements(21L);
        }
    }

    @Test
    void bindDeviceRejectsDeletedAccountTombstoneBeforeActivationLookup() {
        V2BindDeviceRequest request = new V2BindDeviceRequest();
        request.setDeviceSn("SN-DELETED");
        request.setActivationCode("ACT-DELETED");

        V2AccountEntity deleted = new V2AccountEntity();
        deleted.setId(21L);
        deleted.setStatus("deleted");
        when(v2AccountDao.selectById(21L)).thenReturn(deleted);

        UserDetail user = new UserDetail();
        user.setId(21L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);
            try (MockedStatic<MessageUtils> mockedMessageUtils = mockStatic(MessageUtils.class)) {
                mockedMessageUtils.when(() -> MessageUtils.getMessage(ErrorCode.ACCOUNT_DISABLE))
                        .thenReturn("account disabled");

                RenException ex = assertThrows(RenException.class, () -> service.bindDevice(request));

                assertEquals(ErrorCode.ACCOUNT_DISABLE, ex.getCode());
                verify(v2ActivationCodeDao, never()).selectOne(any());
                verify(v2DeviceDao, never()).insert(any());
                verify(v2DeviceBindingDao, never()).insert(any());
            }
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
            verify(factoryEntitlementService).ensureFactoryEntitlements(21L);
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
            verify(resourceEntitlementService).requireSubmitEntitlements(31L, "home", null);
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
            verify(resourceEntitlementService).requireSubmitEntitlements(31L, "home", null);
            verify(v2TaskDao).insert(any(V2TaskEntity.class));
            verify(deviceServerMotionGateway).forwardAcceptedTask(eq("dev-1"), any(V2TaskEntity.class), eq(request));
        }
    }

    @Test
    void submitTaskRejectsNonPrimaryBeforePersistingOrForwarding() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("home");

        UserDetail user = new UserDetail();
        user.setId(31L);
        doThrow(new PrimarySessionException("E_NOT_PRIMARY", "current session is not primary"))
                .when(primarySessionService).requirePrimaryForWrite(31L, "dev-1", "secondary-token");

        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);
            mockedSecurityUser.when(SecurityUser::getToken).thenReturn("secondary-token");

            PrimarySessionException error =
                    assertThrows(PrimarySessionException.class, () -> service.submitTask(" dev-1 ", request));

            assertEquals("E_NOT_PRIMARY", error.getCode());
            verify(primarySessionService).requirePrimaryForWrite(31L, "dev-1", "secondary-token");
            verify(resourceEntitlementService, never()).requireSubmitEntitlements(any(), any(), any());
            verify(v2TaskDao, never()).insert(any());
            verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
        }
    }

    @Test
    void submitTaskRejectsBeforePersistingWhenDeviceIsUpdating() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("home");

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");
        V2DeviceEntity device = runtimeDevice(true, "UPGRADING");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        when(v2DeviceDao.selectById("dev-1")).thenReturn(device);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            SafetyValidationException error =
                    assertThrows(SafetyValidationException.class, () -> service.submitTask("dev-1", request));

            assertEquals("E_DEVICE_UPDATING", error.getErrorCode());
            verify(resourceEntitlementService, never()).requireSubmitEntitlements(any(), any(), any());
            verify(v2TaskDao, never()).insert(any());
            verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
        }
    }

    @Test
    void submitWriteTextRejectsWhenPaperSlotIsEmptyBeforePersisting() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("write_text");
        request.setParams(java.util.Map.of("text", "A"));

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        doThrow(new SafetyValidationException(
                xiaozhi.modules.appv2.service.safety.SafetyErrorCode.E_NO_PAPER,
                "paper slot is marked empty"))
                .when(deviceSupplyService).requirePaperReadyForWrite("dev-1", "write_text");

        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            SafetyValidationException error =
                    assertThrows(SafetyValidationException.class, () -> service.submitTask("dev-1", request));

            assertEquals("E_NO_PAPER", error.getErrorCode());
            verify(deviceSupplyService).requirePaperReadyForWrite("dev-1", "write_text");
            verify(resourceEntitlementService, never()).requireSubmitEntitlements(any(), any(), any());
            verify(v2TaskDao, never()).insert(any());
            verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
        }
    }

    @Test
    void submitWriteTextProjectsToRunPathBeforeDispatch() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("write_text");
        request.setRequestId("req-write-1");
        request.setTraceId("trace-write-1");
        request.setSource("client");
        request.setParams(java.util.Map.of("text", "你好"));
        request.setConstraints(java.util.Map.of("timeout_ms", 120000, "safety_level", "strict"));

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            service.submitTask("dev-1", request);

            org.mockito.ArgumentCaptor<V2TaskEntity> taskCaptor = org.mockito.ArgumentCaptor.forClass(V2TaskEntity.class);
            verify(v2TaskDao).insert(taskCaptor.capture());
            assertEquals("write_text", taskCaptor.getValue().getCapability());
            assertEquals("{\"text\":\"你好\"}", taskCaptor.getValue().getParamsJson());
            assertEquals("run_path", taskCaptor.getValue().getDispatchCapability());
            assertTrue(taskCaptor.getValue().getDispatchParamsJson().contains("\"path\""));

            org.mockito.ArgumentCaptor<V2SubmitTaskRequest> dispatchCaptor =
                    org.mockito.ArgumentCaptor.forClass(V2SubmitTaskRequest.class);
            verify(deviceServerMotionGateway).forwardAcceptedTask(eq("dev-1"), any(V2TaskEntity.class), dispatchCaptor.capture());
            V2SubmitTaskRequest dispatch = dispatchCaptor.getValue();
            assertEquals("run_path", dispatch.getCapability());
            assertEquals("req-write-1", dispatch.getRequestId());
            assertEquals("trace-write-1", dispatch.getTraceId());
            assertEquals("client", dispatch.getSource());
            assertEquals(900, dispatch.getParams().get("feed"));
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> path =
                    (java.util.List<java.util.Map<String, Object>>) dispatch.getParams().get("path");
            assertEquals(14, path.size());
            assertEquals("M", path.get(0).get("cmd"));
        }
    }

    @Test
    void submitVoiceTaskUsesActiveBindingOwnerAndSourceVoice() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("write_text");
        request.setRequestId("voice-req-1");
        java.util.Map<String, Object> voiceprint = new java.util.LinkedHashMap<>();
        voiceprint.put("matched", true);
        voiceprint.put("member_id", 1);
        voiceprint.put("display_name", "Parent");
        voiceprint.put("member_type", "owner");
        voiceprint.put("speaker_ref", "local:parent");
        voiceprint.put("reason", "matched");
        request.setConstraints(java.util.Map.of("voiceprint", voiceprint));
        request.setParams(java.util.Map.of("text", "你好", "font_id", "kai_basic_v1"));

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(41L);
        binding.setDeviceId("dev-voice-1");
        binding.setBindingStatus("active");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);

        V2SubmitTaskResponse response = service.submitVoiceTask("dev-voice-1", request);

        assertEquals("accepted", response.getStatus());
        verify(resourceEntitlementService).requireSubmitEntitlements(41L, "write_text", request.getParams());
        org.mockito.ArgumentCaptor<V2TaskEntity> taskCaptor = org.mockito.ArgumentCaptor.forClass(V2TaskEntity.class);
        verify(v2TaskDao).insert(taskCaptor.capture());
        V2TaskEntity inserted = taskCaptor.getValue();
        assertEquals(41L, inserted.getAccountId());
        assertEquals("dev-voice-1", inserted.getDeviceId());
        assertEquals("voice", inserted.getSource());
        assertTrue(inserted.getConstraintsJson().contains("\"speaker_ref\":\"local:parent\""));
        assertTrue(inserted.getConstraintsJson().contains("\"member_type\":\"owner\""));

        org.mockito.ArgumentCaptor<V2SubmitTaskRequest> dispatchCaptor =
                org.mockito.ArgumentCaptor.forClass(V2SubmitTaskRequest.class);
        verify(deviceServerMotionGateway).forwardAcceptedTask(eq("dev-voice-1"), any(V2TaskEntity.class), dispatchCaptor.capture());
        assertEquals("run_path", dispatchCaptor.getValue().getCapability());
        assertEquals("voice", dispatchCaptor.getValue().getSource());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> dispatchVoiceprint =
                (java.util.Map<String, Object>) dispatchCaptor.getValue().getConstraints().get("voiceprint");
        assertEquals("local:parent", dispatchVoiceprint.get("speaker_ref"));
        assertEquals("owner", dispatchVoiceprint.get("member_type"));
    }

    @Test
    void submitVoiceTaskRejectsUnboundDevice() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("write_text");
        request.setParams(java.util.Map.of("text", "你好"));
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(null);

        try (MockedStatic<MessageUtils> mockedMessageUtils = mockStatic(MessageUtils.class)) {
            mockedMessageUtils.when(() -> MessageUtils.getMessage(org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn("device not found");
            RenException error = assertThrows(RenException.class, () -> service.submitVoiceTask("dev-missing", request));
            assertEquals("device not found", error.getMsg());
        }

        verify(v2TaskDao, never()).insert(any());
        verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
    }

    @Test
    void submitVoiceTaskCreatesPendingApprovalWhenPrimarySessionBlocksVoiceWithoutForwarding() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("write_text");
        request.setRequestId("voice-req-pending");
        request.setParams(java.util.Map.of("text", "hello"));

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(41L);
        binding.setDeviceId("dev-voice-1");
        binding.setBindingStatus("active");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        doThrow(new PrimarySessionException("E_NOT_PRIMARY", "primary session blocks voice write"))
                .when(primarySessionService).requireVoiceAllowedForWrite(41L, "dev-voice-1");

        V2SubmitTaskResponse response = service.submitVoiceTask(" dev-voice-1 ", request);

        assertEquals("pending_primary_approval", response.getStatus());
        assertEquals("primary", response.getApprovalRequiredBy());
        verify(primarySessionService).requireVoiceAllowedForWrite(41L, "dev-voice-1");
        verify(resourceEntitlementService, never()).requireSubmitEntitlements(any(), any(), any());
        org.mockito.ArgumentCaptor<V2TaskEntity> taskCaptor = org.mockito.ArgumentCaptor.forClass(V2TaskEntity.class);
        verify(v2TaskDao).insert(taskCaptor.capture());
        assertEquals("pending_primary_approval", taskCaptor.getValue().getStatus());
        assertEquals("voice", taskCaptor.getValue().getSource());
        verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
        verify(productNotificationOutboxService)
                .enqueuePendingPrimaryVoiceApproval(41L, "dev-voice-1", taskCaptor.getValue().getId());
    }

    @Test
    void approveVoiceTaskRequiresPrimaryAndForwardsPendingVoiceTask() {
        V2TaskEntity task = new V2TaskEntity();
        task.setId("task-pending-1");
        task.setAccountId(41L);
        task.setDeviceId("dev-voice-1");
        task.setRequestId("voice-req-1");
        task.setTraceId("trace-voice-1");
        task.setCapability("write_text");
        task.setSource("voice");
        task.setParamsJson("{\"text\":\"hello\"}");
        task.setConstraintsJson("{\"voiceprint\":{\"speaker_ref\":\"local:parent\"}}");
        task.setStatus("pending_primary_approval");
        when(v2TaskDao.selectById("task-pending-1")).thenReturn(task);

        UserDetail user = new UserDetail();
        user.setId(41L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);
            mockedSecurityUser.when(SecurityUser::getToken).thenReturn("primary-token");

            V2SubmitTaskResponse response = service.approveVoiceTask("task-pending-1", new V2TaskApprovalRequest());

            assertEquals("accepted", response.getStatus());
            verify(primarySessionService).requirePrimaryForWrite(41L, "dev-voice-1", "primary-token");
            verify(resourceEntitlementService).requireSubmitEntitlements(eq(41L), eq("write_text"), any());
            org.mockito.ArgumentCaptor<V2TaskEntity> taskCaptor = org.mockito.ArgumentCaptor.forClass(V2TaskEntity.class);
            verify(v2TaskDao).updateById(taskCaptor.capture());
            assertEquals("accepted", taskCaptor.getValue().getStatus());
            verify(productNotificationOutboxService).resolvePrimaryVoiceApproval("task-pending-1");
            verify(deviceServerMotionGateway).forwardAcceptedTask(eq("dev-voice-1"), eq(task), any(V2SubmitTaskRequest.class));
        }
    }

    @Test
    void rejectVoiceTaskRequiresPrimaryAndMarksTaskRejectedWithoutForwarding() {
        V2TaskEntity task = new V2TaskEntity();
        task.setId("task-pending-2");
        task.setAccountId(41L);
        task.setDeviceId("dev-voice-1");
        task.setCapability("write_text");
        task.setSource("voice");
        task.setStatus("pending_primary_approval");
        when(v2TaskDao.selectById("task-pending-2")).thenReturn(task);
        V2TaskApprovalRequest request = new V2TaskApprovalRequest();
        request.setReason("not now");

        UserDetail user = new UserDetail();
        user.setId(41L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);
            mockedSecurityUser.when(SecurityUser::getToken).thenReturn("primary-token");

            V2SubmitTaskResponse response = service.rejectVoiceTask("task-pending-2", request);

            assertEquals("rejected", response.getStatus());
            verify(primarySessionService).requirePrimaryForWrite(41L, "dev-voice-1", "primary-token");
            org.mockito.ArgumentCaptor<V2TaskEntity> taskCaptor = org.mockito.ArgumentCaptor.forClass(V2TaskEntity.class);
            verify(v2TaskDao).updateById(taskCaptor.capture());
            assertEquals("rejected", taskCaptor.getValue().getStatus());
            assertEquals("E_PRIMARY_REJECTED", taskCaptor.getValue().getErrorCode());
            assertEquals("not now", taskCaptor.getValue().getErrorMessage());
            assertNotNull(taskCaptor.getValue().getFinishedAt());
            verify(productNotificationOutboxService).cancelPrimaryVoiceApproval("task-pending-2");
            verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
        }
    }

    @Test
    void listPendingVoiceTasksReturnsCurrentAccountDevicePendingVoiceTasks() {
        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(41L);
        binding.setDeviceId("dev-voice-1");
        binding.setBindingStatus("active");
        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        V2TaskEntity task = new V2TaskEntity();
        task.setId("task-pending-3");
        task.setAccountId(41L);
        task.setDeviceId("dev-voice-1");
        task.setRequestId("voice-req-3");
        task.setCapability("write_text");
        task.setSource("voice");
        task.setStatus("pending_primary_approval");
        task.setParamsJson("{\"text\":\"hello\"}");
        when(v2TaskDao.selectList(any())).thenReturn(java.util.List.of(task));

        UserDetail user = new UserDetail();
        user.setId(41L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            java.util.List<xiaozhi.modules.appv2.dto.V2PendingVoiceTaskResponse> response =
                    service.listPendingVoiceTasks(" dev-voice-1 ");

            assertEquals(1, response.size());
            assertEquals("task-pending-3", response.get(0).getTaskId());
            assertEquals("pending_primary_approval", response.get(0).getStatus());
            verify(v2TaskDao).selectList(any());
        }
    }

    @Test
    void submitTaskStopsBeforePersistingWhenEntitlementIsMissing() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("write_text");
        request.setParams(java.util.Map.of("text", "hello", "font_id", "kai_premium_v1"));

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        org.mockito.Mockito.doThrow(new EntitlementValidationException("font", "kai_premium_v1"))
                .when(resourceEntitlementService)
                .requireSubmitEntitlements(31L, "write_text", request.getParams());
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            EntitlementValidationException error =
                    assertThrows(EntitlementValidationException.class, () -> service.submitTask("dev-1", request));

            assertEquals("E_NOT_ENTITLED", error.getErrorCode());
            verify(v2TaskDao, never()).insert(any());
            verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
        }
    }

    @Test
    void submitWriteTextBlocksContentBeforePersisting() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("write_text");
        request.setParams(java.util.Map.of("text", "这是一段不当文字"));

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            ContentAuditException error =
                    assertThrows(ContentAuditException.class, () -> service.submitTask("dev-1", request));

            assertEquals("E_CONTENT_BLOCKED", error.getErrorCode());
            verify(contentAuditLogService)
                    .recordBlockedContent(31L, "dev-1", "write_text.text", "这是一段不当文字", "keyword:不当文字");
            verify(v2TaskDao, never()).insert(any());
            verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
        }
    }

    @Test
    void submitDrawGeneratedRejectsInvalidSvgBeforePersisting() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("draw_generated");
        request.setParams(java.util.Map.of("svg_text", "<svg><rect width=\"10\" height=\"10\" fill=\"red\"/></svg>"));

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            DrawingValidationException error =
                    assertThrows(DrawingValidationException.class, () -> service.submitTask("dev-1", request));

            assertEquals("E_INVALID_DRAWING", error.getErrorCode());
            assertEquals("filled_shape", error.getReason());
            verify(v2TaskDao, never()).insert(any());
            verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
        }
    }

    @Test
    void submitDrawGeneratedBlocksPromptContentBeforePersisting() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("draw_generated");
        request.setParams(java.util.Map.of(
                "prompt",
                "\u8fd9\u662f\u4e00\u6bb5\u4e0d\u5f53\u6587\u5b57",
                "svg_text",
                "<svg viewBox=\"0 0 20 20\"><path d=\"M5 5 L15 5\" fill=\"none\" stroke=\"black\"/></svg>"));

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            ContentAuditException error =
                    assertThrows(ContentAuditException.class, () -> service.submitTask("dev-1", request));

            assertEquals("E_CONTENT_BLOCKED", error.getErrorCode());
            verify(contentAuditLogService)
                    .recordBlockedContent(
                            31L,
                            "dev-1",
                            "draw_generated.prompt",
                            "\u8fd9\u662f\u4e00\u6bb5\u4e0d\u5f53\u6587\u5b57",
                            "keyword:\u4e0d\u5f53\u6587\u5b57");
            verify(v2TaskDao, never()).insert(any());
            verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
        }
    }

    @Test
    void submitDrawGeneratedRejectsImplicitStarterAssetFallbackBeforePersisting() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("draw_generated");
        request.setParams(java.util.Map.of("prompt", "cat", "starter_id", "starter_cat"));

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            DrawingValidationException error =
                    assertThrows(DrawingValidationException.class, () -> service.submitTask("dev-1", request));

            assertEquals("E_INVALID_DRAWING", error.getErrorCode());
            assertEquals("starter_asset_not_explicit", error.getReason());
            verify(v2TaskDao, never()).insert(any());
            verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
        }
    }

    @Test
    void submitDrawGeneratedProjectsSvgTextToRunPathBeforeDispatch() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("draw_generated");
        request.setRequestId("req-draw-1");
        request.setTraceId("trace-draw-1");
        request.setSource("client");
        request.setParams(java.util.Map.of(
                "svg_text",
                "<svg viewBox=\"0 0 20 20\"><path d=\"M5 5 L15 5 L15 15 Z\" fill=\"none\" stroke=\"black\"/></svg>"));

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            service.submitTask("dev-1", request);

            org.mockito.ArgumentCaptor<V2TaskEntity> taskCaptor = org.mockito.ArgumentCaptor.forClass(V2TaskEntity.class);
            verify(v2TaskDao).insert(taskCaptor.capture());
            assertEquals("draw_generated", taskCaptor.getValue().getCapability());

            org.mockito.ArgumentCaptor<V2SubmitTaskRequest> dispatchCaptor =
                    org.mockito.ArgumentCaptor.forClass(V2SubmitTaskRequest.class);
            verify(deviceServerMotionGateway).forwardAcceptedTask(eq("dev-1"), any(V2TaskEntity.class), dispatchCaptor.capture());
            V2SubmitTaskRequest dispatch = dispatchCaptor.getValue();
            assertEquals("run_path", dispatch.getCapability());
            assertEquals("req-draw-1", dispatch.getRequestId());
            assertEquals("trace-draw-1", dispatch.getTraceId());
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> path =
                    (java.util.List<java.util.Map<String, Object>>) dispatch.getParams().get("path");
            assertEquals(4, path.size());
            assertEquals("M", path.get(0).get("cmd"));
            assertEquals("L", path.get(3).get("cmd"));
        }
    }

    @Test
    void submitDrawGeneratedProjectsPromptToRunPathBeforeDispatch() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("draw_generated");
        request.setRequestId("req-draw-prompt-1");
        request.setTraceId("trace-draw-prompt-1");
        request.setSource("client");
        request.setParams(java.util.Map.of("prompt", "cat"));

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            service.submitTask("dev-1", request);

            org.mockito.ArgumentCaptor<V2TaskEntity> taskCaptor = org.mockito.ArgumentCaptor.forClass(V2TaskEntity.class);
            verify(v2TaskDao).insert(taskCaptor.capture());
            assertEquals("draw_generated", taskCaptor.getValue().getCapability());
            assertEquals("{\"prompt\":\"cat\"}", taskCaptor.getValue().getParamsJson());

            org.mockito.ArgumentCaptor<V2SubmitTaskRequest> dispatchCaptor =
                    org.mockito.ArgumentCaptor.forClass(V2SubmitTaskRequest.class);
            verify(deviceServerMotionGateway).forwardAcceptedTask(eq("dev-1"), any(V2TaskEntity.class), dispatchCaptor.capture());
            V2SubmitTaskRequest dispatch = dispatchCaptor.getValue();
            assertEquals("run_path", dispatch.getCapability());
            assertEquals("req-draw-prompt-1", dispatch.getRequestId());
            assertEquals("trace-draw-prompt-1", dispatch.getTraceId());
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> path =
                    (java.util.List<java.util.Map<String, Object>>) dispatch.getParams().get("path");
            assertEquals(3, path.size());
            assertEquals("M", path.get(0).get("cmd"));
            assertEquals("L", path.get(2).get("cmd"));
        }
    }

    @Test
    void submitDrawGeneratedProjectsExplicitStarterAssetToRunPathBeforeDispatch() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("draw_generated");
        request.setRequestId("req-draw-starter-1");
        request.setTraceId("trace-draw-starter-1");
        request.setSource("client");
        request.setParams(java.util.Map.of("starter_id", "starter_star", "use_starter_asset", true));

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            service.submitTask("dev-1", request);

            org.mockito.ArgumentCaptor<V2TaskEntity> taskCaptor = org.mockito.ArgumentCaptor.forClass(V2TaskEntity.class);
            verify(v2TaskDao).insert(taskCaptor.capture());
            assertEquals("draw_generated", taskCaptor.getValue().getCapability());
            assertTrue(taskCaptor.getValue().getParamsJson().contains("\"starter_id\":\"starter_star\""));
            assertTrue(taskCaptor.getValue().getParamsJson().contains("\"use_starter_asset\":true"));

            org.mockito.ArgumentCaptor<V2SubmitTaskRequest> dispatchCaptor =
                    org.mockito.ArgumentCaptor.forClass(V2SubmitTaskRequest.class);
            verify(deviceServerMotionGateway).forwardAcceptedTask(eq("dev-1"), any(V2TaskEntity.class), dispatchCaptor.capture());
            V2SubmitTaskRequest dispatch = dispatchCaptor.getValue();
            assertEquals("run_path", dispatch.getCapability());
            assertEquals("req-draw-starter-1", dispatch.getRequestId());
            assertEquals("trace-draw-starter-1", dispatch.getTraceId());
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> path =
                    (java.util.List<java.util.Map<String, Object>>) dispatch.getParams().get("path");
            assertEquals(11, path.size());
            assertEquals("M", path.get(0).get("cmd"));
            assertEquals("L", path.get(10).get("cmd"));
        }
    }

    @Test
    void submitDrawGeneratedProjectsBitmapToRunPathBeforeDispatch() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("draw_generated");
        request.setRequestId("req-draw-bitmap-1");
        request.setTraceId("trace-draw-bitmap-1");
        request.setSource("client");
        request.setParams(java.util.Map.of("bitmap_base64", bitmapPngBase64()));

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            service.submitTask("dev-1", request);

            org.mockito.ArgumentCaptor<V2TaskEntity> taskCaptor = org.mockito.ArgumentCaptor.forClass(V2TaskEntity.class);
            verify(v2TaskDao).insert(taskCaptor.capture());
            assertEquals("draw_generated", taskCaptor.getValue().getCapability());

            org.mockito.ArgumentCaptor<V2SubmitTaskRequest> dispatchCaptor =
                    org.mockito.ArgumentCaptor.forClass(V2SubmitTaskRequest.class);
            verify(deviceServerMotionGateway).forwardAcceptedTask(eq("dev-1"), any(V2TaskEntity.class), dispatchCaptor.capture());
            V2SubmitTaskRequest dispatch = dispatchCaptor.getValue();
            assertEquals("run_path", dispatch.getCapability());
            assertEquals("req-draw-bitmap-1", dispatch.getRequestId());
            assertEquals("trace-draw-bitmap-1", dispatch.getTraceId());
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> path =
                    (java.util.List<java.util.Map<String, Object>>) dispatch.getParams().get("path");
            assertEquals(3, path.size());
            assertEquals("M", path.get(0).get("cmd"));
            assertEquals("L", path.get(2).get("cmd"));
        }
    }

    @Test
    void submitDrawGeneratedFitsPromptIntoDeviceWritableBounds() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("draw_generated");
        request.setRequestId("req-draw-small-workspace");
        request.setParams(java.util.Map.of("prompt", "cat"));

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");
        V2DeviceEntity device = runtimeDevice(true, "IDLE");
        device.setWorkspaceMm("{\"x\":40,\"y\":30,\"z\":10}");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        when(v2DeviceDao.selectById("dev-1")).thenReturn(device);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            service.submitTask("dev-1", request);

            org.mockito.ArgumentCaptor<V2SubmitTaskRequest> dispatchCaptor =
                    org.mockito.ArgumentCaptor.forClass(V2SubmitTaskRequest.class);
            verify(deviceServerMotionGateway).forwardAcceptedTask(eq("dev-1"), any(V2TaskEntity.class), dispatchCaptor.capture());
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> path =
                    (java.util.List<java.util.Map<String, Object>>) dispatchCaptor.getValue().getParams().get("path");
            for (java.util.Map<String, Object> point : path) {
                double x = ((Number) point.get("x")).doubleValue();
                double y = ((Number) point.get("y")).doubleValue();
                org.junit.jupiter.api.Assertions.assertTrue(x >= 5.0 && x <= 35.0, "x outside writable bounds: " + x);
                org.junit.jupiter.api.Assertions.assertTrue(y >= 5.0 && y <= 25.0, "y outside writable bounds: " + y);
            }
        }
    }

    @Test
    void submitDrawGeneratedAppliesSizeAndAlignmentHints() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("draw_generated");
        request.setRequestId("req-draw-layout-hint");
        request.setParams(java.util.Map.of(
                "prompt", "cat",
                "size_hint", "smaller",
                "align", "left"));

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");
        V2DeviceEntity device = runtimeDevice(true, "IDLE");
        device.setWorkspaceMm("{\"x\":40,\"y\":30,\"z\":10}");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        when(v2DeviceDao.selectById("dev-1")).thenReturn(device);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            service.submitTask("dev-1", request);

            org.mockito.ArgumentCaptor<V2SubmitTaskRequest> dispatchCaptor =
                    org.mockito.ArgumentCaptor.forClass(V2SubmitTaskRequest.class);
            verify(deviceServerMotionGateway).forwardAcceptedTask(eq("dev-1"), any(V2TaskEntity.class), dispatchCaptor.capture());
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> path =
                    (java.util.List<java.util.Map<String, Object>>) dispatchCaptor.getValue().getParams().get("path");
            double minX = path.stream().mapToDouble(point -> ((Number) point.get("x")).doubleValue()).min().orElseThrow();
            double maxX = path.stream().mapToDouble(point -> ((Number) point.get("x")).doubleValue()).max().orElseThrow();
            double minY = path.stream().mapToDouble(point -> ((Number) point.get("y")).doubleValue()).min().orElseThrow();
            double maxY = path.stream().mapToDouble(point -> ((Number) point.get("y")).doubleValue()).max().orElseThrow();
            assertEquals(5.0, minX, 0.001);
            org.junit.jupiter.api.Assertions.assertTrue(maxX - minX <= 18.0, "size_hint=smaller was not applied");
            org.junit.jupiter.api.Assertions.assertTrue(minY >= 5.0 && maxY <= 25.0);
        }
    }

    @Test
    void submitRunPathRejectsFeedRateAboveDeviceLimitBeforePersisting() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("run_path");
        request.setParams(java.util.Map.of(
                "feed",
                3001,
                "path",
                java.util.List.of(
                        java.util.Map.of("cmd", "M", "x", 10.0, "y", 10.0, "z", 0.0),
                        java.util.Map.of("cmd", "L", "x", 20.0, "y", 20.0, "z", 0.0))));

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            SafetyValidationException error =
                    assertThrows(SafetyValidationException.class, () -> service.submitTask("dev-1", request));

            assertEquals("E_INVALID_PARAM", error.getErrorCode());
            verify(safetyAuditService).recordBusinessReject(eq(31L), eq("dev-1"), eq("run_path"), eq(error));
            verify(v2TaskDao, never()).insert(any());
            verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
        }
    }

    @Test
    void submitWriteTextRejectsProjectedPathOutsideWritableAreaBeforePersisting() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("write_text");
        request.setParams(java.util.Map.of("text", "A", "canvas_width_mm", 220.0));

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            SafetyValidationException error =
                    assertThrows(SafetyValidationException.class, () -> service.submitTask("dev-1", request));

            assertEquals("E_OUT_OF_RANGE", error.getErrorCode());
            verify(v2TaskDao, never()).insert(any());
            verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
        }
    }

    @Test
    void submitRunPathRejectsWhenCachedRuntimeIsNotHomed() {
        V2SubmitTaskRequest request = safeRunPathRequest();

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");
        V2DeviceEntity device = runtimeDevice(false, "IDLE");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        when(v2DeviceDao.selectById("dev-1")).thenReturn(device);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            SafetyValidationException error =
                    assertThrows(SafetyValidationException.class, () -> service.submitTask("dev-1", request));

            assertEquals("E_NOT_HOMED", error.getErrorCode());
            verify(v2TaskDao, never()).insert(any());
            verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
        }
    }

    @Test
    void submitRunPathRejectsWhenCachedRuntimeIsBusy() {
        V2SubmitTaskRequest request = safeRunPathRequest();

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");
        V2DeviceEntity device = runtimeDevice(true, "RUNNING");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        when(v2DeviceDao.selectById("dev-1")).thenReturn(device);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            SafetyValidationException error =
                    assertThrows(SafetyValidationException.class, () -> service.submitTask("dev-1", request));

            assertEquals("E_DEVICE_BUSY", error.getErrorCode());
            verify(v2TaskDao, never()).insert(any());
            verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
        }
    }

    @Test
    void submitRunPathRejectsWhenCachedRuntimeIsEstop() {
        V2SubmitTaskRequest request = safeRunPathRequest();

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");
        V2DeviceEntity device = runtimeDevice(false, "ESTOP");

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        when(v2DeviceDao.selectById("dev-1")).thenReturn(device);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            SafetyValidationException error =
                    assertThrows(SafetyValidationException.class, () -> service.submitTask("dev-1", request));

            assertEquals("E_ESTOP", error.getErrorCode());
            verify(safetyAuditService).recordBusinessReject(eq(31L), eq("dev-1"), eq("run_path"), eq(error));
            verify(v2TaskDao, never()).insert(any());
            verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
        }
    }

    @Test
    void submitRunPathRejectsWhenCachedRuntimeIsStale() {
        V2SubmitTaskRequest request = safeRunPathRequest();
        request.setTraceId("trace-stale-1");

        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");
        V2DeviceEntity device = runtimeDevice(true, "IDLE");
        device.setRuntimeSeenAt(new java.util.Date(0L));

        lenient().when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        when(v2DeviceDao.selectById("dev-1")).thenReturn(device);
        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            SafetyValidationException error =
                    assertThrows(SafetyValidationException.class, () -> service.submitTask("dev-1", request));

            assertEquals("E_RUNTIME_STALE", error.getErrorCode());
            verify(deviceServerMotionGateway)
                    .forwardRuntimeStatusRefresh("dev-1", 31L, "runtime_stale", "trace-stale-1");
            verify(safetyAuditService).recordBusinessReject(eq(31L), eq("dev-1"), eq("run_path"), eq(error));
            verify(v2TaskDao, never()).insert(any());
            verify(deviceServerMotionGateway, never()).forwardAcceptedTask(any(), any(), any());
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
      void ingestMotionEventMapsProgressPhaseToRunning() {
          java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
          payload.put("task_id", "task-progress");
          payload.put("phase", "progress");
          payload.put("device_id", "dev-progress");
          payload.put("capability", "run_path");
          payload.put("progress", java.util.Map.of("done_segments", 2, "total_segments", 4, "percent", 50));

          V2TaskEntity task = new V2TaskEntity();
          task.setId("task-progress");
          task.setStatus("accepted");
          when(v2TaskDao.selectById("task-progress")).thenReturn(task);

          service.ingestMotionEvent(payload);

          org.mockito.ArgumentCaptor<V2TaskEntity> captor = org.mockito.ArgumentCaptor.forClass(V2TaskEntity.class);
          verify(v2TaskDao).updateById(captor.capture());
          V2TaskEntity updated = captor.getValue();
          assertEquals("running", updated.getStatus());
          assertNotNull(updated.getStartedAt());
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

    @Test
    void ingestMotionEventRecordsPenMileageWhenRunPathNewlyCompletes() {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("task_id", "task-run-path-done");
        payload.put("phase", "done");
        payload.put("device_id", "dev-1");

        V2TaskEntity task = new V2TaskEntity();
        task.setId("task-run-path-done");
        task.setDeviceId("dev-1");
        task.setCapability("run_path");
        task.setStatus("running");
        task.setParamsJson("{\"path\":[{\"cmd\":\"M\",\"x\":0,\"y\":0},{\"cmd\":\"L\",\"x\":3,\"y\":4}]}");
        when(v2TaskDao.selectById("task-run-path-done")).thenReturn(task);

        service.ingestMotionEvent(payload);

        verify(v2TaskDao).updateById(any(V2TaskEntity.class));
        verify(deviceSupplyService)
                .recordCompletedRunPathMileage(
                        "dev-1",
                        "{\"path\":[{\"cmd\":\"M\",\"x\":0,\"y\":0},{\"cmd\":\"L\",\"x\":3,\"y\":4}]}");
    }

    @Test
    void ingestMotionEventRecordsProjectedPenMileageForWriteTextCompletion() {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("task_id", "task-write-done");
        payload.put("phase", "done");
        payload.put("device_id", "dev-1");

        V2TaskEntity task = new V2TaskEntity();
        task.setId("task-write-done");
        task.setDeviceId("dev-1");
        task.setCapability("write_text");
        task.setParamsJson("{\"text\":\"A\"}");
        task.setDispatchCapability("run_path");
        task.setDispatchParamsJson("{\"path\":[{\"cmd\":\"M\",\"x\":0,\"y\":0},{\"cmd\":\"L\",\"x\":6,\"y\":8}]}");
        task.setStatus("running");
        when(v2TaskDao.selectById("task-write-done")).thenReturn(task);

        service.ingestMotionEvent(payload);

        verify(v2TaskDao).updateById(any(V2TaskEntity.class));
        verify(deviceSupplyService)
                .recordCompletedRunPathMileage(
                        "dev-1",
                        "{\"path\":[{\"cmd\":\"M\",\"x\":0,\"y\":0},{\"cmd\":\"L\",\"x\":6,\"y\":8}]}");
    }

    @Test
    void ingestMotionEventDoesNotRecordPenMileageForAlreadyDoneRunPath() {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("task_id", "task-run-path-done");
        payload.put("phase", "done");
        payload.put("device_id", "dev-1");

        V2TaskEntity task = new V2TaskEntity();
        task.setId("task-run-path-done");
        task.setDeviceId("dev-1");
        task.setCapability("run_path");
        task.setStatus("done");
        task.setParamsJson("{\"path\":[{\"cmd\":\"M\",\"x\":0,\"y\":0},{\"cmd\":\"L\",\"x\":3,\"y\":4}]}");
        when(v2TaskDao.selectById("task-run-path-done")).thenReturn(task);

        service.ingestMotionEvent(payload);

        verify(v2TaskDao).updateById(any(V2TaskEntity.class));
        verify(deviceSupplyService, never()).recordCompletedRunPathMileage(any(), any());
    }

    @Test
    void ingestMotionEventMirrorsU1SafetyErrorToAudit() {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("task_id", "task-u1-error");
        payload.put("phase", "failed");
        payload.put("device_id", "dev-1");
        payload.put("error_code", "E002");
        payload.put("error_message", "soft limit exceeded");

        V2TaskEntity task = new V2TaskEntity();
        task.setId("task-u1-error");
        task.setAccountId(31L);
        task.setDeviceId("dev-1");
        task.setCapability("run_path");
        task.setStatus("running");
        when(v2TaskDao.selectById("task-u1-error")).thenReturn(task);

        service.ingestMotionEvent(payload);

        verify(v2TaskDao).updateById(any(V2TaskEntity.class));
        verify(safetyAuditService)
                .recordU1Reject(31L, "dev-1", "run_path", "E002", "soft limit exceeded");
        verify(edgeAClientHub).publishMotionEvent(payload);
    }

    @Test
    void ingestMotionEventMapsRejectedToFailedTerminalState() {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("task_id", "task-rejected");
        payload.put("phase", "rejected");
        payload.put("device_id", "dev-4");

        V2TaskEntity task = new V2TaskEntity();
        task.setId("task-rejected");
        task.setStatus("accepted");
        when(v2TaskDao.selectById("task-rejected")).thenReturn(task);

        service.ingestMotionEvent(payload);

        org.mockito.ArgumentCaptor<V2TaskEntity> captor = org.mockito.ArgumentCaptor.forClass(V2TaskEntity.class);
        verify(v2TaskDao).updateById(captor.capture());
        V2TaskEntity updated = captor.getValue();
        assertEquals("failed", updated.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(updated.getStartedAt());
        org.junit.jupiter.api.Assertions.assertNotNull(updated.getFinishedAt());
    }

    @Test
    void ingestMotionEventUpdatesDeviceRuntimeSnapshotFromResult() {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("task_id", "task-status");
        payload.put("phase", "done");
        payload.put("device_id", "dev-runtime");
        payload.put("ts", 1_715_000_000L);
        payload.put("result", java.util.Map.of(
                "state", "IDLE",
                "homed", true,
                "position", java.util.Map.of("x", 10.0, "y", 5.0, "z", 1.0)));
        when(v2DeviceDao.updateById(any(V2DeviceEntity.class))).thenReturn(1);

        service.ingestMotionEvent(payload);

        org.mockito.ArgumentCaptor<V2DeviceEntity> captor = org.mockito.ArgumentCaptor.forClass(V2DeviceEntity.class);
        verify(v2DeviceDao).updateById(captor.capture());
        V2DeviceEntity updated = captor.getValue();
        assertEquals("dev-runtime", updated.getId());
        assertEquals("IDLE", updated.getRuntimeState());
        assertEquals(Boolean.TRUE, updated.getHomed());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> position = cn.hutool.json.JSONUtil.toBean(updated.getPositionMm(), java.util.Map.class);
        assertEquals(10.0, ((Number) position.get("x")).doubleValue());
        assertEquals(5.0, ((Number) position.get("y")).doubleValue());
        assertEquals(1.0, ((Number) position.get("z")).doubleValue());
        assertEquals(1_715_000_000_000L, updated.getRuntimeSeenAt().getTime());
        assertEquals(1_715_000_000_000L, updated.getLastSeenAt().getTime());
        verify(edgeAClientHub).publishMotionEvent(payload);
    }

    @Test
    void ingestDeviceInfoUpdatesDeviceSnapshotWithoutTouchingTaskState() {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("task_id", "task-info");
        payload.put("device_id", "dev-1");
        payload.put("model", "DLC Motor Control P1 XYYZ");
        payload.put("hw_rev", "DLC_Motor_Control_P1_V1.0_260513");
        payload.put("fw_rev", "fake-u1");
        payload.put("workspace_mm", java.util.Map.of("x", 200.0, "y", 150.0, "z", 50.0));
        when(v2DeviceDao.updateById(any(V2DeviceEntity.class))).thenReturn(1);

        service.ingestDeviceInfo(payload);

        org.mockito.ArgumentCaptor<V2DeviceEntity> captor = org.mockito.ArgumentCaptor.forClass(V2DeviceEntity.class);
        verify(v2DeviceDao).updateById(captor.capture());
        V2DeviceEntity updated = captor.getValue();
        assertEquals("dev-1", updated.getId());
        assertEquals("DLC Motor Control P1 XYYZ", updated.getModel());
        assertEquals("DLC_Motor_Control_P1_V1.0_260513", updated.getHwRev());
        assertEquals("fake-u1", updated.getFwRev());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> workspace = cn.hutool.json.JSONUtil.toBean(updated.getWorkspaceMm(), java.util.Map.class);
        assertEquals(200.0, ((Number) workspace.get("x")).doubleValue());
        assertEquals(150.0, ((Number) workspace.get("y")).doubleValue());
        assertEquals(50.0, ((Number) workspace.get("z")).doubleValue());
        org.junit.jupiter.api.Assertions.assertNotNull(updated.getLastSeenAt());
        verify(v2TaskDao, never()).updateById(any(V2TaskEntity.class));
        verify(edgeAClientHub, never()).publishMotionEvent(any());
        verify(edgeAClientHub).publishDeviceInfo(payload);
    }

    @Test
    void ingestSelfCheckPublishesDeviceEventWithoutTouchingTaskState() {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("device_id", "dev-1");
        payload.put("check_id", "startup");
        payload.put("scope", "startup");
        payload.put("status", "passed");
        payload.put("checks", java.util.Map.of(
                "nvs", java.util.Map.of("ok", true),
                "wifi", java.util.Map.of("ok", true),
                "u1_uart", java.util.Map.of("ok", true),
                "audio", java.util.Map.of("ok", true)));

        service.ingestSelfCheck(payload);

        org.mockito.ArgumentCaptor<V2DeviceSelfCheckEventEntity> selfCheckCaptor =
                org.mockito.ArgumentCaptor.forClass(V2DeviceSelfCheckEventEntity.class);
        verify(v2DeviceSelfCheckEventDao).insert(selfCheckCaptor.capture());
        assertEquals("dev-1", selfCheckCaptor.getValue().getDeviceId());
        assertEquals("startup", selfCheckCaptor.getValue().getCheckId());
        assertEquals("passed", selfCheckCaptor.getValue().getStatus());
        assertTrue(selfCheckCaptor.getValue().getChecksJson().contains("u1_uart"));
        verify(v2TaskDao, never()).updateById(any(V2TaskEntity.class));
        verify(edgeAClientHub, never()).publishMotionEvent(any());
        verify(edgeAClientHub).publishSelfCheck(payload);
    }

    @Test
    void listSelfCheckHistoryRequiresBindingAndReturnsRecentRows() {
        V2DeviceBindingEntity binding = new V2DeviceBindingEntity();
        binding.setAccountId(31L);
        binding.setDeviceId("dev-1");
        binding.setBindingStatus("active");
        when(v2DeviceBindingDao.selectOne(any())).thenReturn(binding);
        V2DeviceSelfCheckEventEntity event = new V2DeviceSelfCheckEventEntity();
        event.setId(9L);
        event.setDeviceId("dev-1");
        event.setCheckId("startup");
        event.setScope("startup");
        event.setStatus("pass");
        event.setSummary("nvs:pass");
        event.setChecksJson("{\"nvs\":{\"status\":\"pass\"}}");
        event.setReportedAt(new java.util.Date(1_715_000_000_000L));
        when(v2DeviceSelfCheckEventDao.selectList(any())).thenReturn(java.util.List.of(event));

        UserDetail user = new UserDetail();
        user.setId(31L);
        try (MockedStatic<SecurityUser> mockedSecurityUser = mockStatic(SecurityUser.class)) {
            mockedSecurityUser.when(SecurityUser::getUser).thenReturn(user);

            var response = service.listSelfCheckHistory(" dev-1 ");

            assertEquals(1, response.size());
            assertEquals(9L, response.get(0).getId());
            assertEquals("dev-1", response.get(0).getDeviceId());
            assertEquals("pass", response.get(0).getStatus());
            verify(v2DeviceBindingDao).selectOne(any());
            verify(v2DeviceSelfCheckEventDao).selectList(any());
        }
    }

    private static V2SubmitTaskRequest safeRunPathRequest() {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability("run_path");
        request.setParams(java.util.Map.of(
                "feed",
                900,
                "path",
                java.util.List.of(
                        java.util.Map.of("cmd", "M", "x", 10.0, "y", 10.0, "z", 0.0),
                        java.util.Map.of("cmd", "L", "x", 20.0, "y", 20.0, "z", 0.0))));
        return request;
    }

    private static V2DeviceEntity runtimeDevice(boolean homed, String state) {
        V2DeviceEntity device = new V2DeviceEntity();
        device.setId("dev-1");
        device.setRuntimeState(state);
        device.setHomed(homed);
        return device;
    }

    private static String bitmapPngBase64() {
        try {
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    image.setRGB(x, y, Color.WHITE.getRGB());
                }
            }
            image.setRGB(1, 1, Color.BLACK.getRGB());
            image.setRGB(2, 1, Color.BLACK.getRGB());
            image.setRGB(2, 2, Color.BLACK.getRGB());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
