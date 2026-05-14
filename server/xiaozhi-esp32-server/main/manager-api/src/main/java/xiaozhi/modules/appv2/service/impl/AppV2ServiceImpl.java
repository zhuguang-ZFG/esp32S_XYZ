package xiaozhi.modules.appv2.service.impl;

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
import xiaozhi.modules.appv2.service.WechatLoginGateway;
import xiaozhi.modules.appv2.service.WechatLoginGateway.WechatSession;
import xiaozhi.modules.security.service.SysUserTokenService;
import xiaozhi.modules.security.user.SecurityUser;
import xiaozhi.modules.sys.dto.SysUserDTO;
import xiaozhi.modules.sys.service.SysUserService;

@Service
@AllArgsConstructor
public class AppV2ServiceImpl implements AppV2Service {
    private static final String ACCOUNT_STATUS_ACTIVE = "active";
    private static final String DEVICE_STATUS_PROVISIONED = "provisioned";
    private static final String DEVICE_STATUS_BOUND = "bound";
    private static final String BINDING_STATUS_ACTIVE = "active";
    private static final String TASK_STATUS_ACCEPTED = "accepted";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final V2AccountDao v2AccountDao;
    private final V2DeviceDao v2DeviceDao;
    private final V2DeviceBindingDao v2DeviceBindingDao;
    private final V2ActivationCodeDao v2ActivationCodeDao;
    private final V2TaskDao v2TaskDao;
    private final SysUserService sysUserService;
    private final SysUserTokenService sysUserTokenService;
    private final WechatLoginGateway wechatLoginGateway;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2LoginResponse login(V2LoginRequest request) {
        // v2 §11.1: 服务端用临时 code 经 jscode2session 换取 unionid，
        // 严禁直接信任客户端传入的 unionid。
        WechatSession session = wechatLoginGateway.exchange(request.getCode());
        String unionid = session.getUnionid();
        String openid = session.getOpenid();

        String username = buildUsername(unionid);
        SysUserDTO sysUser = sysUserService.getByUsername(username);
        if (sysUser == null) {
            SysUserDTO createUser = new SysUserDTO();
            createUser.setUsername(username);
            // 每个 v2 账号独立随机密码，避免共用弱口令被旧 /user/login 反向冒用。
            createUser.setPassword(generateAccountSecret());
            createUser.setRealName(StringUtils.defaultIfBlank(request.getDisplayName(), unionid));
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
            account.setDisplayName(StringUtils.defaultIfBlank(request.getDisplayName(), unionid));
            account.setStatus(ACCOUNT_STATUS_ACTIVE);
            v2AccountDao.insert(account);
        } else {
            account.setOpenid(openid);
            if (StringUtils.isNotBlank(request.getDisplayName())) {
                account.setDisplayName(request.getDisplayName());
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
        V2ActivationCodeEntity activation = v2ActivationCodeDao.selectOne(new LambdaQueryWrapper<V2ActivationCodeEntity>()
                .eq(V2ActivationCodeEntity::getDeviceSn, request.getDeviceSn())
                .eq(V2ActivationCodeEntity::getActivationCode, request.getActivationCode())
                .last("limit 1"));
        if (activation == null) {
            throw new RenException(ErrorCode.ACTIVATION_CODE_ERROR);
        }
        if (activation.getExpiresAt() != null && activation.getExpiresAt().before(new Date())) {
            throw new RenException(ErrorCode.ACTIVATION_CODE_ERROR);
        }
        if (DEVICE_STATUS_BOUND.equalsIgnoreCase(activation.getStatus()) || activation.getUsedAt() != null) {
            throw new RenException(ErrorCode.DEVICE_ALREADY_ACTIVATED);
        }

        String deviceId = StringUtils.defaultIfBlank(activation.getDeviceId(), buildDeviceId(request.getDeviceSn()));
        V2DeviceEntity device = v2DeviceDao.selectById(deviceId);
        if (device == null) {
            device = new V2DeviceEntity();
            device.setId(deviceId);
            device.setDeviceSn(request.getDeviceSn());
            device.setStatus(DEVICE_STATUS_BOUND);
            v2DeviceDao.insert(device);
        } else {
            device.setStatus(DEVICE_STATUS_BOUND);
            v2DeviceDao.updateById(device);
        }

        V2DeviceBindingEntity binding = v2DeviceBindingDao.selectOne(new LambdaQueryWrapper<V2DeviceBindingEntity>()
                .eq(V2DeviceBindingEntity::getAccountId, user.getId())
                .eq(V2DeviceBindingEntity::getDeviceId, deviceId)
                .eq(V2DeviceBindingEntity::getBindingStatus, BINDING_STATUS_ACTIVE)
                .last("limit 1"));
        if (binding == null) {
            binding = new V2DeviceBindingEntity();
            binding.setAccountId(user.getId());
            binding.setDeviceId(deviceId);
            binding.setBindingStatus(BINDING_STATUS_ACTIVE);
            binding.setIsPrimary(Boolean.TRUE);
            binding.setBoundAt(new Date());
            v2DeviceBindingDao.insert(binding);
        }

        activation.setDeviceId(deviceId);
        activation.setStatus(DEVICE_STATUS_BOUND);
        activation.setUsedAt(new Date());
        v2ActivationCodeDao.updateById(activation);
        return new V2BindDeviceResponse(user.getId(), deviceId, BINDING_STATUS_ACTIVE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2SubmitTaskResponse submitTask(String deviceId, V2SubmitTaskRequest request) {
        UserDetail user = requireUser();
        ensureActiveBinding(user.getId(), deviceId);

        if (StringUtils.isNotBlank(request.getRequestId())) {
            V2TaskEntity existing = v2TaskDao.selectOne(new LambdaQueryWrapper<V2TaskEntity>()
                    .eq(V2TaskEntity::getAccountId, user.getId())
                    .eq(V2TaskEntity::getDeviceId, deviceId)
                    .eq(V2TaskEntity::getRequestId, request.getRequestId())
                    .last("limit 1"));
            if (existing != null) {
                return new V2SubmitTaskResponse(existing.getId(), existing.getStatus());
            }
        }

        V2TaskEntity task = new V2TaskEntity();
        task.setId(UUID.randomUUID().toString());
        task.setAccountId(user.getId());
        task.setDeviceId(deviceId);
        task.setRequestId(emptyToNull(request.getRequestId()));
        task.setTraceId(emptyToNull(request.getTraceId()));
        task.setCapability(request.getCapability());
        task.setSource(StringUtils.defaultIfBlank(request.getSource(), "client"));
        task.setParamsJson(toJson(request.getParams()));
        task.setConstraintsJson(toJson(request.getConstraints()));
        task.setStatus(TASK_STATUS_ACCEPTED);
        v2TaskDao.insert(task);
        return new V2SubmitTaskResponse(task.getId(), task.getStatus());
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

    private static String emptyToNull(String value) {
        return StringUtils.isBlank(value) ? null : value;
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
