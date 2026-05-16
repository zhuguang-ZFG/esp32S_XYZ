package xiaozhi.modules.appv2.service.impl;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import cn.hutool.json.JSONUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.TokenDTO;
import xiaozhi.common.user.UserDetail;
import xiaozhi.common.utils.Result;
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
import xiaozhi.modules.appv2.dto.V2PendingVoiceTaskResponse;
import xiaozhi.modules.appv2.dto.V2SelfCheckHistoryResponse;
import xiaozhi.modules.appv2.dto.V2SubmitTaskRequest;
import xiaozhi.modules.appv2.dto.V2SubmitTaskResponse;
import xiaozhi.modules.appv2.dto.V2TaskApprovalRequest;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2ActivationCodeEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2DeviceEntity;
import xiaozhi.modules.appv2.entity.V2DeviceSelfCheckEventEntity;
import xiaozhi.modules.appv2.entity.V2TaskEntity;
import xiaozhi.modules.appv2.service.AppV2Service;
import xiaozhi.modules.appv2.service.DeviceServerMotionGateway;
import xiaozhi.modules.appv2.service.DeviceSupplyService;
import xiaozhi.modules.appv2.service.PrimarySessionException;
import xiaozhi.modules.appv2.service.PrimarySessionService;
import xiaozhi.modules.appv2.service.ProductNotificationOutboxService;
import xiaozhi.modules.appv2.service.WechatLoginGateway;
import xiaozhi.modules.appv2.service.WechatLoginGateway.WechatSession;
import xiaozhi.modules.appv2.service.contentaudit.ContentAuditException;
import xiaozhi.modules.appv2.service.contentaudit.ContentAuditLogService;
import xiaozhi.modules.appv2.service.contentaudit.ContentAuditService;
import xiaozhi.modules.appv2.service.graphic.SingleLineSvgValidator;
import xiaozhi.modules.appv2.service.projection.DrawGeneratedProjectionService;
import xiaozhi.modules.appv2.service.projection.RunPathProjection;
import xiaozhi.modules.appv2.service.projection.WriteTextProjectionService;
import xiaozhi.modules.appv2.service.resource.FactoryEntitlementService;
import xiaozhi.modules.appv2.service.resource.ResourceEntitlementService;
import xiaozhi.modules.appv2.service.safety.DeviceCaps;
import xiaozhi.modules.appv2.service.safety.DeviceRuntimeState;
import xiaozhi.modules.appv2.service.safety.PathBounds;
import xiaozhi.modules.appv2.service.safety.SafeMarginMm;
import xiaozhi.modules.appv2.service.safety.SafetyAuditService;
import xiaozhi.modules.appv2.service.safety.SafetyDecision;
import xiaozhi.modules.appv2.service.safety.SafetyErrorCode;
import xiaozhi.modules.appv2.service.safety.SafetyValidationException;
import xiaozhi.modules.appv2.service.safety.SafetyValidator;
import xiaozhi.modules.appv2.service.safety.WorkspaceBounds;
import xiaozhi.modules.appv2.ws.EdgeAClientHub;
import xiaozhi.modules.security.service.SysUserTokenService;
import xiaozhi.modules.security.user.SecurityUser;
import xiaozhi.modules.sys.dto.SysUserDTO;
import xiaozhi.modules.sys.service.SysUserService;

@Service
@AllArgsConstructor
@Slf4j
public class AppV2ServiceImpl implements AppV2Service {
    private static final String ACCOUNT_STATUS_ACTIVE = "active";
    private static final String ACCOUNT_STATUS_DELETED = "deleted";
    private static final String DEVICE_STATUS_PROVISIONED = "provisioned";
    private static final String DEVICE_STATUS_BOUND = "bound";
    private static final String BINDING_STATUS_ACTIVE = "active";
    private static final String TASK_STATUS_ACCEPTED = "accepted";
    private static final String TASK_STATUS_PENDING_PRIMARY_APPROVAL = "pending_primary_approval";
    private static final String TASK_STATUS_RUNNING = "running";
    private static final String TASK_STATUS_DONE = "done";
    private static final String TASK_STATUS_FAILED = "failed";
    private static final String TASK_STATUS_CANCELLED = "cancelled";
    private static final String TASK_STATUS_REJECTED = "rejected";
    private static final String TASK_SOURCE_VOICE = "voice";
    private static final double DEFAULT_WORKSPACE_X_MM = 100.0;
    private static final double DEFAULT_WORKSPACE_Y_MM = 100.0;
    private static final double DEFAULT_WORKSPACE_Z_MM = 10.0;
    private static final double DEFAULT_SAFE_MARGIN_XY_MM = 5.0;
    private static final double DEFAULT_SAFE_MARGIN_Z_MM = 0.0;
    private static final double DEFAULT_MAX_FEED_RATE = 3000.0;
    private static final long RUNTIME_STALE_MS = 5_000L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final V2AccountDao v2AccountDao;
    private final V2DeviceDao v2DeviceDao;
    private final V2DeviceBindingDao v2DeviceBindingDao;
    private final V2ActivationCodeDao v2ActivationCodeDao;
    private final V2TaskDao v2TaskDao;
    private final V2DeviceSelfCheckEventDao v2DeviceSelfCheckEventDao;
    private final SysUserService sysUserService;
    private final SysUserTokenService sysUserTokenService;
    private final WechatLoginGateway wechatLoginGateway;
    private final DeviceServerMotionGateway deviceServerMotionGateway;
    private final DeviceSupplyService deviceSupplyService;
    private final PrimarySessionService primarySessionService;
    private final ProductNotificationOutboxService productNotificationOutboxService;
    private final EdgeAClientHub edgeAClientHub;
    private final WriteTextProjectionService writeTextProjectionService;
    private final ContentAuditService contentAuditService;
    private final ContentAuditLogService contentAuditLogService;
    private final SingleLineSvgValidator singleLineSvgValidator;
    private final DrawGeneratedProjectionService drawGeneratedProjectionService;
    private final SafetyAuditService safetyAuditService;
    private final FactoryEntitlementService factoryEntitlementService;
    private final ResourceEntitlementService resourceEntitlementService;
    private final SafetyValidator safetyValidator = new SafetyValidator();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2LoginResponse login(V2LoginRequest request) {
        String displayName = normalizedOptional(request.getDisplayName());
        // v2 §11.1: 服务端用临时 code 经 jscode2session 换取 unionid，
        // 严禁直接信任客户端传入的 unionid。
        WechatSession session = wechatLoginGateway.exchange(normalizedRequired(request.getCode()));
        String unionid = session.getUnionid();
        String openid = session.getOpenid();

        V2AccountEntity account = v2AccountDao.selectOne(new LambdaQueryWrapper<V2AccountEntity>()
                .eq(V2AccountEntity::getUnionid, unionid)
                .last("limit 1"));
        if (account != null && ACCOUNT_STATUS_DELETED.equalsIgnoreCase(account.getStatus())) {
            throw new RenException(ErrorCode.ACCOUNT_DISABLE);
        }

        String username = buildUsername(unionid);
        SysUserDTO sysUser = sysUserService.getByUsername(username);
        if (sysUser == null) {
            SysUserDTO createUser = new SysUserDTO();
            createUser.setUsername(username);
            // 每个 v2 账号独立随机密码，避免共用弱口令被旧 /user/login 反向冒用。
            createUser.setPassword(generateAccountSecret());
            createUser.setRealName(StringUtils.defaultIfBlank(displayName, unionid));
            sysUserService.save(createUser);
            sysUser = sysUserService.getByUsername(username);
        }

        if (account == null) {
            account = new V2AccountEntity();
            account.setId(sysUser.getId());
            account.setUnionid(unionid);
            account.setOpenid(openid);
            account.setDisplayName(StringUtils.defaultIfBlank(displayName, unionid));
            account.setStatus(ACCOUNT_STATUS_ACTIVE);
            v2AccountDao.insert(account);
        } else {
            account.setOpenid(openid);
            if (StringUtils.isNotBlank(displayName)) {
                account.setDisplayName(displayName);
            }
            account.setStatus(ACCOUNT_STATUS_ACTIVE);
            v2AccountDao.updateById(account);
        }

        Result<TokenDTO> tokenResult = sysUserTokenService.createToken(sysUser.getId());
        TokenDTO tokenDTO = tokenResult.getData();
        account.setPrimarySessionId(tokenDTO.getToken());
        v2AccountDao.updateById(account);
        return new V2LoginResponse(account.getId(), tokenDTO.getToken(), tokenDTO.getExpire());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2BindDeviceResponse bindDevice(V2BindDeviceRequest request) {
        UserDetail user = requireUser();
        String deviceSn = normalizedRequired(request.getDeviceSn());
        String activationCode = normalizedRequired(request.getActivationCode());
        V2ActivationCodeEntity activation = v2ActivationCodeDao.selectOne(new LambdaQueryWrapper<V2ActivationCodeEntity>()
                .eq(V2ActivationCodeEntity::getDeviceSn, deviceSn)
                .eq(V2ActivationCodeEntity::getActivationCode, activationCode)
                .last("limit 1"));
        if (activation == null) {
            throw new RenException(ErrorCode.ACTIVATION_CODE_ERROR);
        }
        if (activation.getExpiresAt() != null && activation.getExpiresAt().before(new Date())) {
            throw new RenException(ErrorCode.ACTIVATION_CODE_ERROR);
        }
        String deviceId = StringUtils.defaultIfBlank(activation.getDeviceId(), buildDeviceId(deviceSn));
        V2DeviceBindingEntity binding = activeBindingByDevice(deviceId);
        if (binding != null) {
            if (!user.getId().equals(binding.getAccountId())) {
                throw new RenException(ErrorCode.DEVICE_ALREADY_ACTIVATED);
            }
            ensureBoundDeviceState(deviceId, deviceSn);
            syncActivationBindingState(activation, deviceId);
            factoryEntitlementService.ensureFactoryEntitlements(user.getId());
            return new V2BindDeviceResponse(user.getId(), deviceId, BINDING_STATUS_ACTIVE);
        }
        if (DEVICE_STATUS_BOUND.equalsIgnoreCase(activation.getStatus()) || activation.getUsedAt() != null) {
            throw new RenException(ErrorCode.DEVICE_ALREADY_ACTIVATED);
        }

        ensureBoundDeviceState(deviceId, deviceSn);
        binding = new V2DeviceBindingEntity();
        binding.setAccountId(user.getId());
        binding.setDeviceId(deviceId);
        binding.setBindingStatus(BINDING_STATUS_ACTIVE);
        binding.setIsPrimary(Boolean.TRUE);
        binding.setBoundAt(new Date());
        v2DeviceBindingDao.insert(binding);

        syncActivationBindingState(activation, deviceId);
        factoryEntitlementService.ensureFactoryEntitlements(user.getId());
        return new V2BindDeviceResponse(user.getId(), deviceId, BINDING_STATUS_ACTIVE);
    }

    private void ensureBoundDeviceState(String deviceId, String deviceSn) {
        V2DeviceEntity device = v2DeviceDao.selectById(deviceId);
        if (device == null) {
            device = new V2DeviceEntity();
            device.setId(deviceId);
            device.setDeviceSn(deviceSn);
            device.setStatus(DEVICE_STATUS_BOUND);
            v2DeviceDao.insert(device);
        } else {
            device.setStatus(DEVICE_STATUS_BOUND);
            if (StringUtils.isBlank(device.getDeviceSn())) {
                device.setDeviceSn(deviceSn);
            }
            v2DeviceDao.updateById(device);
        }
    }

    private void syncActivationBindingState(V2ActivationCodeEntity activation, String deviceId) {
        activation.setDeviceId(deviceId);
        activation.setStatus(DEVICE_STATUS_BOUND);
        if (activation.getUsedAt() == null) {
            activation.setUsedAt(new Date());
        }
        v2ActivationCodeDao.updateById(activation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2SubmitTaskResponse submitTask(String deviceId, V2SubmitTaskRequest request) {
        UserDetail user = requireUser();
        String normalizedDeviceId = normalizedRequired(deviceId);
        primarySessionService.requirePrimaryForWrite(user.getId(), normalizedDeviceId, SecurityUser.getToken());
        return submitTaskForAccount(user.getId(), normalizedDeviceId, request, "client");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2SubmitTaskResponse submitVoiceTask(String deviceId, V2SubmitTaskRequest request) {
        String normalizedDeviceId = normalizedRequired(deviceId);
        V2DeviceBindingEntity binding = activeBindingByDevice(normalizedDeviceId);
        if (binding == null || binding.getAccountId() == null) {
            throw new RenException(ErrorCode.DEVICE_NOT_EXIST);
        }
        try {
            primarySessionService.requireVoiceAllowedForWrite(binding.getAccountId(), normalizedDeviceId);
        } catch (PrimarySessionException e) {
            return submitPendingVoiceTaskForPrimaryApproval(binding.getAccountId(), normalizedDeviceId, request);
        }
        return submitTaskForAccount(binding.getAccountId(), normalizedDeviceId, request, TASK_SOURCE_VOICE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2SubmitTaskResponse approveVoiceTask(String taskId, V2TaskApprovalRequest request) {
        UserDetail user = requireUser();
        V2TaskEntity task = requirePendingVoiceTask(taskId, user.getId());
        primarySessionService.requirePrimaryForWrite(user.getId(), task.getDeviceId(), SecurityUser.getToken());
        V2SubmitTaskRequest taskRequest = requestFromTask(task);
        requireDeviceNotUpdating(task.getDeviceId());
        deviceSupplyService.requirePaperReadyForWrite(task.getDeviceId(), task.getCapability());
        try {
            auditSubmitRequest(task.getCapability(), taskRequest.getParams());
        } catch (ContentAuditException e) {
            recordContentAuditReject(task.getAccountId(), task.getDeviceId(), task.getCapability(), taskRequest.getParams(), e);
            throw e;
        }
        resourceEntitlementService.requireSubmitEntitlements(task.getAccountId(), task.getCapability(), taskRequest.getParams());
        V2SubmitTaskRequest dispatchRequest = buildDispatchRequest(task.getDeviceId(), taskRequest);
        task.setDispatchCapability(dispatchRequest.getCapability());
        task.setDispatchParamsJson(toJson(dispatchRequest.getParams()));
        try {
            requireSafeDispatch(task.getAccountId(), task.getDeviceId(), dispatchRequest);
        } catch (SafetyValidationException e) {
            recordSafetyReject(task.getAccountId(), task.getDeviceId(), dispatchRequest.getCapability(), e);
            throw e;
        }
        task.setStatus(TASK_STATUS_ACCEPTED);
        task.setErrorCode(null);
        task.setErrorMessage(null);
        v2TaskDao.updateById(task);
        productNotificationOutboxService.resolvePrimaryVoiceApproval(task.getId());
        deviceServerMotionGateway.forwardAcceptedTask(task.getDeviceId(), task, dispatchRequest);
        return new V2SubmitTaskResponse(task.getId(), task.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2SubmitTaskResponse rejectVoiceTask(String taskId, V2TaskApprovalRequest request) {
        UserDetail user = requireUser();
        V2TaskEntity task = requirePendingVoiceTask(taskId, user.getId());
        primarySessionService.requirePrimaryForWrite(user.getId(), task.getDeviceId(), SecurityUser.getToken());
        task.setStatus(TASK_STATUS_REJECTED);
        task.setErrorCode("E_PRIMARY_REJECTED");
        task.setErrorMessage(StringUtils.left(StringUtils.defaultIfBlank(request == null ? null : request.getReason(), "primary rejected voice task"), 255));
        task.setFinishedAt(new Date());
        v2TaskDao.updateById(task);
        productNotificationOutboxService.cancelPrimaryVoiceApproval(task.getId());
        return new V2SubmitTaskResponse(task.getId(), task.getStatus());
    }

    @Override
    public List<V2PendingVoiceTaskResponse> listPendingVoiceTasks(String deviceId) {
        UserDetail user = requireUser();
        String normalizedDeviceId = normalizedRequired(deviceId);
        ensureActiveBinding(user.getId(), normalizedDeviceId);
        return v2TaskDao.selectList(new LambdaQueryWrapper<V2TaskEntity>()
                .eq(V2TaskEntity::getAccountId, user.getId())
                .eq(V2TaskEntity::getDeviceId, normalizedDeviceId)
                .eq(V2TaskEntity::getSource, TASK_SOURCE_VOICE)
                .eq(V2TaskEntity::getStatus, TASK_STATUS_PENDING_PRIMARY_APPROVAL)
                .orderByDesc(V2TaskEntity::getCreatedAt))
                .stream()
                .map(task -> new V2PendingVoiceTaskResponse(
                        task.getId(),
                        task.getDeviceId(),
                        task.getRequestId(),
                        task.getCapability(),
                        task.getParamsJson(),
                        task.getConstraintsJson(),
                        task.getStatus(),
                        task.getCreatedAt()))
                .toList();
    }

    private V2SubmitTaskResponse submitTaskForAccount(
            Long accountId,
            String deviceId,
            V2SubmitTaskRequest request,
            String defaultSource) {
        String normalizedDeviceId = normalizedRequired(deviceId);
        String requestId = normalizedOptional(request.getRequestId());
        String traceId = normalizedOptional(request.getTraceId());
        String capability = normalizedRequired(request.getCapability());
        String source = normalizedOptional(request.getSource());
        ensureActiveBinding(accountId, normalizedDeviceId);
        requireDeviceNotUpdating(normalizedDeviceId);
        deviceSupplyService.requirePaperReadyForWrite(normalizedDeviceId, capability);
        try {
            auditSubmitRequest(capability, request.getParams());
        } catch (ContentAuditException e) {
            recordContentAuditReject(accountId, normalizedDeviceId, capability, request.getParams(), e);
            throw e;
        }
        resourceEntitlementService.requireSubmitEntitlements(accountId, capability, request.getParams());

        if (StringUtils.isNotBlank(requestId)) {
            V2TaskEntity existing = v2TaskDao.selectOne(new LambdaQueryWrapper<V2TaskEntity>()
                    .eq(V2TaskEntity::getAccountId, accountId)
                    .eq(V2TaskEntity::getDeviceId, normalizedDeviceId)
                    .eq(V2TaskEntity::getRequestId, requestId)
                    .last("limit 1"));
            if (existing != null) {
                return new V2SubmitTaskResponse(existing.getId(), existing.getStatus());
            }
        }

        V2TaskEntity task = new V2TaskEntity();
        task.setId(UUID.randomUUID().toString());
        task.setAccountId(accountId);
        task.setDeviceId(normalizedDeviceId);
        task.setRequestId(requestId);
        task.setTraceId(traceId);
        task.setCapability(capability);
        task.setSource(StringUtils.defaultIfBlank(source, defaultSource));
        task.setParamsJson(toJson(request.getParams()));
        task.setConstraintsJson(toJson(request.getConstraints()));
        task.setStatus(TASK_STATUS_ACCEPTED);
        request.setRequestId(requestId);
        request.setTraceId(traceId);
        request.setCapability(capability);
        request.setSource(task.getSource());
        V2SubmitTaskRequest dispatchRequest = buildDispatchRequest(normalizedDeviceId, request);
        task.setDispatchCapability(dispatchRequest.getCapability());
        task.setDispatchParamsJson(toJson(dispatchRequest.getParams()));
        try {
            requireSafeDispatch(accountId, normalizedDeviceId, dispatchRequest);
        } catch (SafetyValidationException e) {
            recordSafetyReject(accountId, normalizedDeviceId, dispatchRequest.getCapability(), e);
            throw e;
        }
        v2TaskDao.insert(task);
        deviceServerMotionGateway.forwardAcceptedTask(normalizedDeviceId, task, dispatchRequest);
        return new V2SubmitTaskResponse(task.getId(), task.getStatus());
    }

    private V2SubmitTaskResponse submitPendingVoiceTaskForPrimaryApproval(
            Long accountId,
            String deviceId,
            V2SubmitTaskRequest request) {
        String normalizedDeviceId = normalizedRequired(deviceId);
        String requestId = normalizedOptional(request.getRequestId());
        String traceId = normalizedOptional(request.getTraceId());
        String capability = normalizedRequired(request.getCapability());
        ensureActiveBinding(accountId, normalizedDeviceId);

        if (StringUtils.isNotBlank(requestId)) {
            V2TaskEntity existing = v2TaskDao.selectOne(new LambdaQueryWrapper<V2TaskEntity>()
                    .eq(V2TaskEntity::getAccountId, accountId)
                    .eq(V2TaskEntity::getDeviceId, normalizedDeviceId)
                    .eq(V2TaskEntity::getRequestId, requestId)
                    .last("limit 1"));
            if (existing != null) {
                return new V2SubmitTaskResponse(existing.getId(), existing.getStatus(), "primary");
            }
        }

        V2TaskEntity task = new V2TaskEntity();
        task.setId(UUID.randomUUID().toString());
        task.setAccountId(accountId);
        task.setDeviceId(normalizedDeviceId);
        task.setRequestId(requestId);
        task.setTraceId(traceId);
        task.setCapability(capability);
        task.setSource(TASK_SOURCE_VOICE);
        task.setParamsJson(toJson(request.getParams()));
        task.setConstraintsJson(toJson(request.getConstraints()));
        task.setStatus(TASK_STATUS_PENDING_PRIMARY_APPROVAL);
        v2TaskDao.insert(task);
        productNotificationOutboxService.enqueuePendingPrimaryVoiceApproval(
                accountId,
                normalizedDeviceId,
                task.getId());
        return new V2SubmitTaskResponse(task.getId(), task.getStatus(), "primary");
    }

    private V2TaskEntity requirePendingVoiceTask(String taskId, Long accountId) {
        String normalizedTaskId = normalizedRequired(taskId);
        V2TaskEntity task = v2TaskDao.selectById(normalizedTaskId);
        if (task == null || !accountId.equals(task.getAccountId())) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        if (!TASK_SOURCE_VOICE.equals(task.getSource())
                || !TASK_STATUS_PENDING_PRIMARY_APPROVAL.equals(task.getStatus())) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        return task;
    }

    private V2SubmitTaskRequest requestFromTask(V2TaskEntity task) {
        V2SubmitTaskRequest request = new V2SubmitTaskRequest();
        request.setCapability(task.getCapability());
        request.setRequestId(task.getRequestId());
        request.setTraceId(task.getTraceId());
        request.setSource(task.getSource());
        request.setParams(jsonToMap(task.getParamsJson()));
        request.setConstraints(jsonToMap(task.getConstraintsJson()));
        return request;
    }

    private void requireDeviceNotUpdating(String deviceId) {
        V2DeviceEntity device = v2DeviceDao.selectById(deviceId);
        if (isDeviceUpdating(device)) {
            throw new SafetyValidationException(SafetyErrorCode.E_DEVICE_UPDATING, "device is updating");
        }
    }

    private static boolean isDeviceUpdating(V2DeviceEntity device) {
        return device != null
                && (isUpdatingState(device.getStatus()) || isUpdatingState(device.getRuntimeState()));
    }

    private static boolean isUpdatingState(String state) {
        if (StringUtils.isBlank(state)) {
            return false;
        }
        String normalized = state.trim().toUpperCase(java.util.Locale.ROOT);
        return "UPDATING".equals(normalized) || "UPGRADING".equals(normalized);
    }

    private void auditSubmitRequest(String capability, Map<String, Object> params) {
        if ("write_text".equals(capability)) {
            auditInboundTextField("write_text.text", params == null ? null : params.get("text"));
            return;
        }
        if ("draw_generated".equals(capability)) {
            Object prompt = params == null ? null : params.get("prompt");
            Object svgText = params == null ? null : params.get("svg_text");
            Object transcript = params == null ? null : params.get("transcript");
            auditInboundTextField("draw_generated.prompt", prompt);
            auditInboundTextField("draw_generated.svg_text", svgText);
            auditInboundTextField("draw_generated.transcript", transcript);
            if (svgText != null) {
                singleLineSvgValidator.requireValid(stringValue(svgText));
            }
            return;
        }
    }

    private void auditInboundTextField(String path, Object value) {
        String raw = stringValue(value);
        try {
            contentAuditService.requireInboundTextAllowed(raw);
        } catch (ContentAuditException e) {
            throw new ContentAuditException(e.getRuleHit(), path, raw);
        }
    }

    private void recordContentAuditReject(
            Long accountId,
            String deviceId,
            String capability,
            Map<String, Object> params,
            ContentAuditException error) {
        try {
            String path = StringUtils.defaultIfBlank(error.getAuditPath(), capability + ".text");
            String raw = error.getAuditRaw();
            if (raw == null) {
                raw = stringValue(params == null ? null : params.get("text"));
            }
            contentAuditLogService.recordBlockedContent(
                    accountId,
                    deviceId,
                    path,
                    raw,
                    error.getRuleHit());
        } catch (Exception auditError) {
            log.warn("content audit insert failed device_id={} capability={}: {}", deviceId, capability, auditError.getMessage(), auditError);
        }
    }

    private V2SubmitTaskRequest buildDispatchRequest(String deviceId, V2SubmitTaskRequest request) {
        if (!"write_text".equals(request.getCapability())) {
            if ("draw_generated".equals(request.getCapability())) {
                return buildDrawGeneratedDispatchRequest(deviceId, request);
            }
            return request;
        }

        RunPathProjection projection = writeTextProjectionService.project(request.getParams());
        return buildProjectedRunPathRequest(request, projection);
    }

    private V2SubmitTaskRequest buildDrawGeneratedDispatchRequest(String deviceId, V2SubmitTaskRequest request) {
        V2DeviceEntity device = v2DeviceDao.selectById(deviceId);
        DeviceCaps caps = loadDeviceCaps(device);
        Map<String, Object> params = withWritableLayoutDefaults(request.getParams(), caps.writableBounds());
        RunPathProjection projection = drawGeneratedProjectionService.project(params);
        return buildProjectedRunPathRequest(request, projection);
    }

    private Map<String, Object> withWritableLayoutDefaults(Map<String, Object> params, WorkspaceBounds writableBounds) {
        Map<String, Object> resolved = new java.util.LinkedHashMap<>();
        if (params != null) {
            resolved.putAll(params);
        }

        double writableWidth = writableBounds.getMaxX() - writableBounds.getMinX();
        double writableHeight = writableBounds.getMaxY() - writableBounds.getMinY();
        if (writableWidth <= 0.0 || writableHeight <= 0.0) {
            throw new SafetyValidationException(SafetyErrorCode.E_INVALID_PARAM, "invalid writable bounds");
        }

        double scale = layoutScaleHint(resolved);
        double canvasWidth = doubleValue(resolved.get("canvas_width_mm"), writableWidth * scale);
        double canvasHeight = doubleValue(resolved.get("canvas_height_mm"), writableHeight * scale);
        canvasWidth = Math.min(canvasWidth, writableWidth);
        canvasHeight = Math.min(canvasHeight, writableHeight);
        if (canvasWidth <= 0.0 || canvasHeight <= 0.0) {
            throw new SafetyValidationException(SafetyErrorCode.E_INVALID_PARAM, "invalid canvas");
        }

        resolved.put("canvas_width_mm", canvasWidth);
        resolved.put("canvas_height_mm", canvasHeight);
        resolved.putIfAbsent("origin_x_mm", writableBounds.getMinX());
        resolved.putIfAbsent("origin_y_mm", writableBounds.getMinY());
        return resolved;
    }

    private static double layoutScaleHint(Map<String, Object> params) {
        String sizeHint = StringUtils.defaultString(stringValue(firstNonNull(
                params.get("size_hint"),
                params.get("layout_size"),
                params.get("scale_hint")))).toLowerCase(java.util.Locale.ROOT);
        return switch (sizeHint) {
            case "larger", "large", "bigger", "big" -> 1.0;
            case "smaller", "small" -> 0.6;
            case "more_margin", "more-margin", "margin_more" -> 0.8;
            default -> 0.9;
        };
    }

    private V2SubmitTaskRequest buildProjectedRunPathRequest(V2SubmitTaskRequest request, RunPathProjection projection) {
        V2SubmitTaskRequest dispatch = new V2SubmitTaskRequest();
        dispatch.setCapability("run_path");
        dispatch.setRequestId(request.getRequestId());
        dispatch.setTraceId(request.getTraceId());
        dispatch.setSource(request.getSource());
        dispatch.setParams(projection.getParams());
        dispatch.setConstraints(request.getConstraints());
        return dispatch;
    }

    private void requireSafeDispatch(Long accountId, String deviceId, V2SubmitTaskRequest dispatchRequest) {
        String capability = normalizedRequired(dispatchRequest.getCapability());
        if (!"run_path".equals(capability) && !"move_abs".equals(capability)) {
            return;
        }

        V2DeviceEntity device = v2DeviceDao.selectById(deviceId);
        DeviceCaps caps = loadDeviceCaps(device);
        requireFreshRuntime(accountId, deviceId, dispatchRequest, device);
        DeviceRuntimeState runtimeState = loadRuntimeState(device);
        PathBounds pathBounds = extractPathBounds(capability, dispatchRequest.getParams());
        Double feedRate = extractFeedRate(dispatchRequest.getParams());
        SafetyDecision decision = safetyValidator.validate(
                capability,
                caps,
                runtimeState,
                pathBounds,
                feedRate);
        if (!decision.isAllowed()) {
            throw new SafetyValidationException(decision);
        }
    }

    private void recordSafetyReject(Long accountId, String deviceId, String capability, SafetyValidationException error) {
        try {
            safetyAuditService.recordBusinessReject(accountId, deviceId, capability, error);
        } catch (Exception auditError) {
            log.warn("safety audit insert failed device_id={} capability={}: {}", deviceId, capability, auditError.getMessage(), auditError);
        }
    }

    private void recordU1SafetyRejectFromMotionEvent(Map<String, Object> payload) {
        String errorCode = stringValue(payload.get("error_code"));
        if (StringUtils.isBlank(errorCode)) {
            return;
        }

        V2TaskEntity task = null;
        String taskId = stringValue(payload.get("task_id"));
        if (StringUtils.isNotBlank(taskId)) {
            task = v2TaskDao.selectById(taskId);
        }

        Long accountId = task == null ? null : task.getAccountId();
        String deviceId = StringUtils.defaultIfBlank(stringValue(payload.get("device_id")), task == null ? null : task.getDeviceId());
        String capability = StringUtils.defaultIfBlank(stringValue(payload.get("capability")), task == null ? null : task.getCapability());
        if (StringUtils.isBlank(deviceId) || StringUtils.isBlank(capability)) {
            return;
        }

        try {
            safetyAuditService.recordU1Reject(
                    accountId,
                    deviceId,
                    capability,
                    errorCode,
                    stringValue(payload.get("error_message")));
        } catch (Exception auditError) {
            log.warn("U1 safety audit insert failed device_id={} capability={}: {}", deviceId, capability, auditError.getMessage(), auditError);
        }
    }

    private DeviceCaps loadDeviceCaps(V2DeviceEntity device) {
        WorkspaceBounds workspace = defaultWorkspaceBounds();
        if (device != null && StringUtils.isNotBlank(device.getWorkspaceMm())) {
            workspace = workspaceBoundsFromSnapshot(device.getWorkspaceMm());
        }
        return DeviceCaps.of(
                workspace,
                SafeMarginMm.of(DEFAULT_SAFE_MARGIN_XY_MM, DEFAULT_SAFE_MARGIN_XY_MM, DEFAULT_SAFE_MARGIN_Z_MM),
                DEFAULT_MAX_FEED_RATE);
    }

    private static DeviceRuntimeState loadRuntimeState(V2DeviceEntity device) {
        if (device == null || StringUtils.isBlank(device.getRuntimeState()) || device.getHomed() == null) {
            return DeviceRuntimeState.of(true, "IDLE");
        }
        return DeviceRuntimeState.of(Boolean.TRUE.equals(device.getHomed()), device.getRuntimeState());
    }

    private void requireFreshRuntime(Long accountId, String deviceId, V2SubmitTaskRequest dispatchRequest, V2DeviceEntity device) {
        if (device == null || device.getRuntimeSeenAt() == null) {
            return;
        }
        long ageMs = System.currentTimeMillis() - device.getRuntimeSeenAt().getTime();
        if (ageMs > RUNTIME_STALE_MS) {
            requestRuntimeStatusRefresh(accountId, deviceId, dispatchRequest);
            throw new SafetyValidationException(SafetyErrorCode.E_RUNTIME_STALE, "cached runtime status is stale");
        }
    }

    private void requestRuntimeStatusRefresh(Long accountId, String deviceId, V2SubmitTaskRequest dispatchRequest) {
        try {
            deviceServerMotionGateway.forwardRuntimeStatusRefresh(
                    deviceId,
                    accountId,
                    "runtime_stale",
                    dispatchRequest == null ? null : dispatchRequest.getTraceId());
        } catch (Exception refreshError) {
            log.warn("runtime status refresh request failed device_id={}: {}", deviceId, refreshError.getMessage(), refreshError);
        }
    }

    private static WorkspaceBounds defaultWorkspaceBounds() {
        return WorkspaceBounds.of(0.0, DEFAULT_WORKSPACE_X_MM, 0.0, DEFAULT_WORKSPACE_Y_MM, 0.0, DEFAULT_WORKSPACE_Z_MM);
    }

    private static WorkspaceBounds workspaceBoundsFromSnapshot(String workspaceJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> workspace = JSONUtil.toBean(workspaceJson, Map.class);
            double minX = doubleValue(firstNonNull(workspace.get("x_min"), workspace.get("min_x")), 0.0);
            double maxX = doubleValue(firstNonNull(workspace.get("x_max"), workspace.get("max_x"), workspace.get("x")), DEFAULT_WORKSPACE_X_MM);
            double minY = doubleValue(firstNonNull(workspace.get("y_min"), workspace.get("min_y")), 0.0);
            double maxY = doubleValue(firstNonNull(workspace.get("y_max"), workspace.get("max_y"), workspace.get("y")), DEFAULT_WORKSPACE_Y_MM);
            double minZ = doubleValue(firstNonNull(workspace.get("z_min"), workspace.get("min_z")), 0.0);
            double maxZ = doubleValue(firstNonNull(workspace.get("z_max"), workspace.get("max_z"), workspace.get("z")), DEFAULT_WORKSPACE_Z_MM);
            return WorkspaceBounds.of(minX, maxX, minY, maxY, minZ, maxZ);
        } catch (RuntimeException e) {
            throw new SafetyValidationException(SafetyErrorCode.E_INVALID_PARAM, "invalid device workspace snapshot");
        }
    }

    private static PathBounds extractPathBounds(String capability, Map<String, Object> params) {
        if ("move_abs".equals(capability)) {
            double x = requiredDouble(params, "x");
            double y = requiredDouble(params, "y");
            double z = doubleValue(params == null ? null : params.get("z"), 0.0);
            return PathBounds.of(x, x, y, y, z, z);
        }

        Object rawPath = params == null ? null : params.get("path");
        if (!(rawPath instanceof List<?> path) || path.isEmpty()) {
            throw new SafetyValidationException(SafetyErrorCode.E_INVALID_PARAM, "run_path requires params.path");
        }

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Object item : path) {
            if (!(item instanceof Map<?, ?> point)) {
                throw new SafetyValidationException(SafetyErrorCode.E_INVALID_PARAM, "run_path point is invalid");
            }
            double x = requiredDouble(point, "x");
            double y = requiredDouble(point, "y");
            double z = doubleValue(point.get("z"), 0.0);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        return PathBounds.of(minX, maxX, minY, maxY, minZ, maxZ);
    }

    private static Double extractFeedRate(Map<String, Object> params) {
        if (params == null) {
            return null;
        }
        Object value = firstNonNull(params.get("feed_rate"), params.get("feed"));
        return value == null ? null : doubleValue(value, 0.0);
    }

    private static double requiredDouble(Map<?, ?> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            throw new SafetyValidationException(SafetyErrorCode.E_INVALID_PARAM, key + " is required");
        }
        return doubleValue(value, 0.0);
    }

    private static double doubleValue(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new SafetyValidationException(SafetyErrorCode.E_INVALID_PARAM, "number is invalid");
        }
    }

    @Override
    public void ingestMotionEvent(Map<String, Object> payload) {
        log.info(
                "motion_event ingest task_id={} phase={} device_id={} capability={}",
                payload.get("task_id"),
                payload.get("phase"),
                payload.get("device_id"),
                payload.get("capability"));
        try {
            updateTaskFromMotionEvent(payload);
            updateDeviceRuntimeFromMotionEvent(payload);
            recordU1SafetyRejectFromMotionEvent(payload);
        } catch (Exception e) {
            log.error("motion_event 落库失败 task_id={}: {}", payload.get("task_id"), e.getMessage(), e);
        }
        edgeAClientHub.publishMotionEvent(payload);
    }

    @Override
    public void ingestDeviceInfo(Map<String, Object> payload) {
        log.info(
                "device_info ingest device_id={} task_id={} model={} hw_rev={} fw_rev={} workspace_mm={}",
                payload.get("device_id"),
                payload.get("task_id"),
                payload.get("model"),
                payload.get("hw_rev"),
                payload.get("fw_rev"),
                payload.get("workspace_mm"));
        try {
            updateDeviceFromDeviceInfo(payload);
        } catch (Exception e) {
            log.error("device_info 快照落库失败 device_id={}: {}", payload.get("device_id"), e.getMessage(), e);
        }
        edgeAClientHub.publishDeviceInfo(payload);
    }

    @Override
    public void ingestSelfCheck(Map<String, Object> payload) {
        log.info(
                "self_check ingest device_id={} check_id={} status={}",
                payload.get("device_id"),
                payload.get("check_id"),
                payload.get("status"));
        try {
            persistSelfCheck(payload);
        } catch (Exception e) {
            log.error("self_check history persist failed device_id={}: {}", payload.get("device_id"), e.getMessage(), e);
        }
        edgeAClientHub.publishSelfCheck(payload);
    }

    @Override
    public List<V2SelfCheckHistoryResponse> listSelfCheckHistory(String deviceId) {
        String normalizedDeviceId = normalizedRequired(deviceId);
        ensureActiveBinding(requireUser().getId(), normalizedDeviceId);
        return v2DeviceSelfCheckEventDao.selectList(new LambdaQueryWrapper<V2DeviceSelfCheckEventEntity>()
                .eq(V2DeviceSelfCheckEventEntity::getDeviceId, normalizedDeviceId)
                .orderByDesc(V2DeviceSelfCheckEventEntity::getReportedAt)
                .last("limit 5"))
                .stream()
                .map(V2SelfCheckHistoryResponse::fromEntity)
                .toList();
    }

    private void persistSelfCheck(Map<String, Object> payload) {
        String deviceId = stringValue(payload.get("device_id"));
        if (StringUtils.isBlank(deviceId)) {
            log.warn("self_check history skipped: missing device_id");
            return;
        }
        V2DeviceSelfCheckEventEntity event = new V2DeviceSelfCheckEventEntity();
        event.setDeviceId(deviceId);
        event.setCheckId(StringUtils.defaultIfBlank(stringValue(payload.get("check_id")), "startup"));
        event.setScope(StringUtils.defaultIfBlank(stringValue(payload.get("scope")), "startup"));
        event.setStatus(StringUtils.defaultIfBlank(stringValue(payload.get("status")), "pending"));
        event.setChecksJson(toJsonOrNull(payload.get("checks")));
        event.setPayloadJson(JSONUtil.toJsonStr(payload));
        event.setSummary(buildSelfCheckSummary(payload.get("checks")));
        event.setReportedAt(parseReportedAt(payload.get("ts")));
        v2DeviceSelfCheckEventDao.insert(event);
    }

    private String buildSelfCheckSummary(Object checks) {
        if (checks == null) {
            return null;
        }
        return StringUtils.abbreviate(JSONUtil.toJsonStr(checks), 512);
    }

    private String toJsonOrNull(Object value) {
        return value == null ? null : JSONUtil.toJsonStr(value);
    }

    private Date parseReportedAt(Object value) {
        if (value == null) {
            return new Date();
        }
        if (value instanceof Number number) {
            long ts = number.longValue();
            if (ts > 0 && ts < 10_000_000_000L) {
                ts *= 1000L;
            }
            return new Date(ts);
        }
        String text = stringValue(value);
        if (StringUtils.isBlank(text)) {
            return new Date();
        }
        try {
            long ts = Long.parseLong(text);
            if (ts > 0 && ts < 10_000_000_000L) {
                ts *= 1000L;
            }
            return new Date(ts);
        } catch (NumberFormatException ignored) {
            try {
                return Date.from(Instant.parse(text));
            } catch (DateTimeParseException ignoredAgain) {
                return new Date();
            }
        }
    }

    private void updateDeviceFromDeviceInfo(Map<String, Object> payload) {
        String deviceId = stringValue(payload.get("device_id"));
        if (StringUtils.isBlank(deviceId)) {
            log.warn("device_info snapshot skipped: missing device_id");
            return;
        }

        V2DeviceEntity device = new V2DeviceEntity();
        device.setId(deviceId);
        device.setModel(stringValue(payload.get("model")));
        device.setHwRev(stringValue(payload.get("hw_rev")));
        device.setFwRev(stringValue(payload.get("fw_rev")));
        Object workspace = payload.get("workspace_mm");
        if (workspace != null) {
            device.setWorkspaceMm(JSONUtil.toJsonStr(workspace));
        }
        device.setLastSeenAt(new Date());

        int updated = v2DeviceDao.updateById(device);
        if (updated == 0) {
            log.warn("device_info snapshot update affected no rows device_id={}", deviceId);
        }
    }

    private void updateDeviceRuntimeFromMotionEvent(Map<String, Object> payload) {
        String deviceId = stringValue(payload.get("device_id"));
        if (StringUtils.isBlank(deviceId)) {
            return;
        }

        Map<String, Object> runtime = extractRuntimePayload(payload);
        if (runtime == null) {
            return;
        }

        String state = stringValue(runtime.get("state"));
        Boolean homed = booleanValue(runtime.get("homed"));
        Object position = firstNonNull(runtime.get("position_mm"), runtime.get("position"));
        if (StringUtils.isBlank(state) && homed == null && position == null) {
            return;
        }

        V2DeviceEntity device = new V2DeviceEntity();
        device.setId(deviceId);
        if (StringUtils.isNotBlank(state)) {
            device.setRuntimeState(state.toUpperCase(java.util.Locale.ROOT));
        }
        if (homed != null) {
            device.setHomed(homed);
        }
        if (position instanceof Map<?, ?> positionMap && !positionMap.isEmpty()) {
            device.setPositionMm(JSONUtil.toJsonStr(positionMap));
        }
        Date seenAt = toDate(firstNonNull(payload.get("ts"), payload.get("timestamp")));
        device.setRuntimeSeenAt(seenAt != null ? seenAt : new Date());
        device.setLastSeenAt(device.getRuntimeSeenAt());

        int updated = v2DeviceDao.updateById(device);
        if (updated == 0) {
            log.warn("device runtime snapshot update affected no rows device_id={}", deviceId);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractRuntimePayload(Map<String, Object> payload) {
        Object result = payload.get("result");
        if (result instanceof Map<?, ?> resultMap
                && (resultMap.containsKey("state") || resultMap.containsKey("homed") || resultMap.containsKey("position"))) {
            return (Map<String, Object>) resultMap;
        }
        if (payload.containsKey("state") || payload.containsKey("homed") || payload.containsKey("position")) {
            return payload;
        }
        return null;
    }

    private static String buildUsername(String unionid) {
        return "wx:" + unionid;
    }

    private static String buildDeviceId(String deviceSn) {
        return "dev_" + deviceSn.replaceAll("[^A-Za-z0-9]", "_");
    }

    private static String generateAccountSecret() {
        // 32 bytes random ≈ 256 bit；写入 sys_user 后只用作内部凭据，不向外回显。
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String toJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return JSONUtil.toJsonStr(value);
    }

    private static Map<String, Object> jsonToMap(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = JSONUtil.toBean(value, Map.class);
        return map;
    }

    private void updateTaskFromMotionEvent(Map<String, Object> payload) {
        String taskId = stringValue(payload.get("task_id"));
        if (StringUtils.isBlank(taskId)) {
            return;
        }
        V2TaskEntity task = v2TaskDao.selectById(taskId.trim());
        if (task == null) {
            log.warn("motion_event ignored: task not found task_id={}", taskId);
            return;
        }

        String previousStatus = task.getStatus();
        String normalizedStatus = normalizeTaskStatus(payload.get("phase"));
        Date startedAt = extractStartedAt(payload);
        Date finishedAt = extractFinishedAt(payload);
        if (StringUtils.isNotBlank(normalizedStatus)) {
            task.setStatus(normalizedStatus);
        }
        if (TASK_STATUS_RUNNING.equals(task.getStatus()) && task.getStartedAt() == null) {
            task.setStartedAt(startedAt != null ? startedAt : new Date());
        }
        if (isTerminalStatus(task.getStatus())) {
            if (task.getStartedAt() == null) {
                task.setStartedAt(startedAt != null ? startedAt : new Date());
            }
            task.setFinishedAt(finishedAt != null ? finishedAt : new Date());
        }

        String errorCode = stringValue(payload.get("error_code"));
        String errorMessage = stringValue(payload.get("error_message"));
        if (StringUtils.isNotBlank(errorCode)) {
            task.setErrorCode(errorCode);
        }
        if (StringUtils.isNotBlank(errorMessage)) {
            task.setErrorMessage(errorMessage);
        }
        if (TASK_STATUS_DONE.equals(task.getStatus())) {
            task.setErrorCode(null);
            task.setErrorMessage(null);
        }

        String resultJson = extractResultJson(payload);
        if (resultJson != null) {
            task.setResultJson(resultJson);
        }
        v2TaskDao.updateById(task);
        recordRunPathMileageIfNewlyDone(task, previousStatus);
    }

    private void recordRunPathMileageIfNewlyDone(V2TaskEntity task, String previousStatus) {
        if (task == null
                || !TASK_STATUS_DONE.equals(task.getStatus())
                || TASK_STATUS_DONE.equals(previousStatus)) {
            return;
        }
        String mileageCapability = StringUtils.defaultIfBlank(task.getDispatchCapability(), task.getCapability());
        if (!"run_path".equalsIgnoreCase(StringUtils.trimToEmpty(mileageCapability))) {
            return;
        }
        String mileageParamsJson = StringUtils.defaultIfBlank(task.getDispatchParamsJson(), task.getParamsJson());
        try {
            deviceSupplyService.recordCompletedRunPathMileage(task.getDeviceId(), mileageParamsJson);
        } catch (Exception e) {
            log.warn("run_path pen mileage update failed task_id={} device_id={}: {}", task.getId(), task.getDeviceId(), e.getMessage(), e);
        }
    }

    private static String extractResultJson(Map<String, Object> payload) {
        Object result = payload.get("result");
        if (result instanceof Map<?, ?> resultMap && !resultMap.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) resultMap;
            return toJson(cast);
        }
        if (result != null) {
            return JSONUtil.toJsonStr(result);
        }
        Object resultJson = payload.get("result_json");
        if (resultJson instanceof String text && StringUtils.isNotBlank(text)) {
            return text;
        }
        return null;
    }

    private static boolean isTerminalStatus(String status) {
        return TASK_STATUS_DONE.equals(status)
                || TASK_STATUS_FAILED.equals(status)
                || TASK_STATUS_CANCELLED.equals(status)
                || TASK_STATUS_REJECTED.equals(status);
    }

    private static String normalizeTaskStatus(Object phaseValue) {
        String phase = stringValue(phaseValue);
        if (StringUtils.isBlank(phase)) {
            return null;
        }
        return switch (phase.trim().toLowerCase()) {
            case TASK_STATUS_ACCEPTED -> TASK_STATUS_ACCEPTED;
            case "started", "start", "in_progress", "progress", TASK_STATUS_RUNNING -> TASK_STATUS_RUNNING;
            case "success", "succeeded", "completed", TASK_STATUS_DONE -> TASK_STATUS_DONE;
            case "error", "errored", TASK_STATUS_FAILED -> TASK_STATUS_FAILED;
            case "canceled", TASK_STATUS_CANCELLED -> TASK_STATUS_CANCELLED;
            case "rejected" -> TASK_STATUS_FAILED;
            default -> {
                log.warn("motion_event 未知 phase={}，跳过状态更新", phase);
                yield null;
            }
        };
    }

    private static Date extractStartedAt(Map<String, Object> payload) {
        Date explicitStartedAt = toDate(payload.get("started_at"));
        if (explicitStartedAt != null) {
            return explicitStartedAt;
        }
        return toDate(firstNonNull(payload.get("ts"), payload.get("timestamp")));
    }

    private static Date extractFinishedAt(Map<String, Object> payload) {
        Date explicitFinishedAt = toDate(payload.get("finished_at"));
        if (explicitFinishedAt != null) {
            return explicitFinishedAt;
        }
        return toDate(firstNonNull(payload.get("ts"), payload.get("timestamp"), payload.get("started_at")));
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Date toDate(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Date date) {
            return date;
        }
        if (raw instanceof Number number) {
            long value = number.longValue();
            if (value > 0L && value < 10_000_000_000L) {
                value *= 1000L;
            }
            return new Date(value);
        }
        String text = stringValue(raw);
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return toDate(Long.parseLong(text));
        } catch (NumberFormatException ignored) {
            try {
                return Date.from(Instant.parse(text));
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.isBlank(text) ? null : text;
    }

    private static String emptyToNull(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }

    private static Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static String normalizedRequired(String value) {
        String normalized = normalizedOptional(value);
        if (normalized == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        return normalized;
    }

    private static String normalizedOptional(String value) {
        return emptyToNull(value == null ? null : value.trim());
    }

    private UserDetail requireUser() {
        UserDetail user = SecurityUser.getUser();
        if (user == null || user.getId() == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        V2AccountEntity account = v2AccountDao.selectById(user.getId());
        if (account != null && ACCOUNT_STATUS_DELETED.equalsIgnoreCase(account.getStatus())) {
            throw new RenException(ErrorCode.ACCOUNT_DISABLE);
        }
        return user;
    }

    private void ensureActiveBinding(Long accountId, String deviceId) {
        V2DeviceBindingEntity binding = v2DeviceBindingDao.selectOne(new LambdaQueryWrapper<V2DeviceBindingEntity>()
                .eq(V2DeviceBindingEntity::getAccountId, accountId)
                .eq(V2DeviceBindingEntity::getDeviceId, deviceId)
                .eq(V2DeviceBindingEntity::getBindingStatus, BINDING_STATUS_ACTIVE)
                .orderByDesc(V2DeviceBindingEntity::getUpdatedAt)
                .orderByDesc(V2DeviceBindingEntity::getId)
                .last("limit 1"));
        if (binding == null) {
            throw new RenException(ErrorCode.DEVICE_NOT_EXIST);
        }
    }

    private V2DeviceBindingEntity activeBindingByDevice(String deviceId) {
        return v2DeviceBindingDao.selectOne(new LambdaQueryWrapper<V2DeviceBindingEntity>()
                .eq(V2DeviceBindingEntity::getDeviceId, deviceId)
                .eq(V2DeviceBindingEntity::getBindingStatus, BINDING_STATUS_ACTIVE)
                .orderByDesc(V2DeviceBindingEntity::getUpdatedAt)
                .orderByDesc(V2DeviceBindingEntity::getId)
                .last("limit 1"));
    }
}
