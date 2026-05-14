package xiaozhi.modules.appv2.service.impl;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.security.SecureRandom;
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
import xiaozhi.modules.appv2.dao.V2TaskDao;
import xiaozhi.modules.appv2.dto.V2BindDeviceRequest;
import xiaozhi.modules.appv2.dto.V2BindDeviceResponse;
import xiaozhi.modules.appv2.dto.V2LoginRequest;
import xiaozhi.modules.appv2.dto.V2LoginResponse;
import xiaozhi.modules.appv2.dto.V2SubmitTaskRequest;
import xiaozhi.modules.appv2.dto.V2SubmitTaskResponse;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2ActivationCodeEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2DeviceEntity;
import xiaozhi.modules.appv2.entity.V2TaskEntity;
import xiaozhi.modules.appv2.service.AppV2Service;
import xiaozhi.modules.appv2.service.DeviceServerMotionGateway;
import xiaozhi.modules.appv2.service.WechatLoginGateway;
import xiaozhi.modules.appv2.service.WechatLoginGateway.WechatSession;
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
    private static final String DEVICE_STATUS_PROVISIONED = "provisioned";
    private static final String DEVICE_STATUS_BOUND = "bound";
    private static final String BINDING_STATUS_ACTIVE = "active";
    private static final String TASK_STATUS_ACCEPTED = "accepted";
    private static final String TASK_STATUS_RUNNING = "running";
    private static final String TASK_STATUS_DONE = "done";
    private static final String TASK_STATUS_FAILED = "failed";
    private static final String TASK_STATUS_CANCELLED = "cancelled";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final V2AccountDao v2AccountDao;
    private final V2DeviceDao v2DeviceDao;
    private final V2DeviceBindingDao v2DeviceBindingDao;
    private final V2ActivationCodeDao v2ActivationCodeDao;
    private final V2TaskDao v2TaskDao;
    private final SysUserService sysUserService;
    private final SysUserTokenService sysUserTokenService;
    private final WechatLoginGateway wechatLoginGateway;
    private final DeviceServerMotionGateway deviceServerMotionGateway;
    private final EdgeAClientHub edgeAClientHub;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2LoginResponse login(V2LoginRequest request) {
        String displayName = normalizedOptional(request.getDisplayName());
        // v2 §11.1: 服务端用临时 code 经 jscode2session 换取 unionid，
        // 严禁直接信任客户端传入的 unionid。
        WechatSession session = wechatLoginGateway.exchange(normalizedRequired(request.getCode()));
        String unionid = session.getUnionid();
        String openid = session.getOpenid();

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

        V2AccountEntity account = v2AccountDao.selectOne(new LambdaQueryWrapper<V2AccountEntity>()
                .eq(V2AccountEntity::getUnionid, unionid)
                .last("limit 1"));
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
        V2DeviceBindingEntity binding = v2DeviceBindingDao.selectOne(new LambdaQueryWrapper<V2DeviceBindingEntity>()
                .eq(V2DeviceBindingEntity::getDeviceId, deviceId)
                .eq(V2DeviceBindingEntity::getBindingStatus, BINDING_STATUS_ACTIVE)
                .last("limit 1"));
        if (binding != null) {
            if (!user.getId().equals(binding.getAccountId())) {
                throw new RenException(ErrorCode.DEVICE_ALREADY_ACTIVATED);
            }
            ensureBoundDeviceState(deviceId, deviceSn);
            syncActivationBindingState(activation, deviceId);
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
        String requestId = normalizedOptional(request.getRequestId());
        String traceId = normalizedOptional(request.getTraceId());
        String capability = normalizedRequired(request.getCapability());
        String source = normalizedOptional(request.getSource());
        ensureActiveBinding(user.getId(), normalizedDeviceId);

        if (StringUtils.isNotBlank(requestId)) {
            V2TaskEntity existing = v2TaskDao.selectOne(new LambdaQueryWrapper<V2TaskEntity>()
                    .eq(V2TaskEntity::getAccountId, user.getId())
                    .eq(V2TaskEntity::getDeviceId, normalizedDeviceId)
                    .eq(V2TaskEntity::getRequestId, requestId)
                    .last("limit 1"));
            if (existing != null) {
                return new V2SubmitTaskResponse(existing.getId(), existing.getStatus());
            }
        }

        V2TaskEntity task = new V2TaskEntity();
        task.setId(UUID.randomUUID().toString());
        task.setAccountId(user.getId());
        task.setDeviceId(normalizedDeviceId);
        task.setRequestId(requestId);
        task.setTraceId(traceId);
        task.setCapability(capability);
        task.setSource(StringUtils.defaultIfBlank(source, "client"));
        task.setParamsJson(toJson(request.getParams()));
        task.setConstraintsJson(toJson(request.getConstraints()));
        task.setStatus(TASK_STATUS_ACCEPTED);
        v2TaskDao.insert(task);
        request.setRequestId(requestId);
        request.setTraceId(traceId);
        request.setCapability(capability);
        request.setSource(source);
        deviceServerMotionGateway.forwardAcceptedTask(normalizedDeviceId, task, request);
        return new V2SubmitTaskResponse(task.getId(), task.getStatus());
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
        } catch (Exception e) {
            log.error("motion_event 落库失败 task_id={}: {}", payload.get("task_id"), e.getMessage(), e);
        }
        edgeAClientHub.publishMotionEvent(payload);
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
                || TASK_STATUS_CANCELLED.equals(status);
    }

    private static String normalizeTaskStatus(Object phaseValue) {
        String phase = stringValue(phaseValue);
        if (StringUtils.isBlank(phase)) {
            return null;
        }
        return switch (phase.trim().toLowerCase()) {
            case TASK_STATUS_ACCEPTED -> TASK_STATUS_ACCEPTED;
            case "started", "start", "in_progress", TASK_STATUS_RUNNING -> TASK_STATUS_RUNNING;
            case "success", "succeeded", "completed", TASK_STATUS_DONE -> TASK_STATUS_DONE;
            case "error", "errored", TASK_STATUS_FAILED -> TASK_STATUS_FAILED;
            case "canceled", TASK_STATUS_CANCELLED -> TASK_STATUS_CANCELLED;
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

    private static UserDetail requireUser() {
        UserDetail user = SecurityUser.getUser();
        if (user == null || user.getId() == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        return user;
    }

    private void ensureActiveBinding(Long accountId, String deviceId) {
        V2DeviceBindingEntity binding = v2DeviceBindingDao.selectOne(new LambdaQueryWrapper<V2DeviceBindingEntity>()
                .eq(V2DeviceBindingEntity::getAccountId, accountId)
                .eq(V2DeviceBindingEntity::getDeviceId, deviceId)
                .eq(V2DeviceBindingEntity::getBindingStatus, BINDING_STATUS_ACTIVE)
                .last("limit 1"));
        if (binding == null) {
            throw new RenException(ErrorCode.DEVICE_NOT_EXIST);
        }
    }
}
