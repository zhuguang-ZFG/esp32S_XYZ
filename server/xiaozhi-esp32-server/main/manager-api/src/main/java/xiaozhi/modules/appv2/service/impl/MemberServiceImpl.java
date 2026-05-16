package xiaozhi.modules.appv2.service.impl;

import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.AllArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.user.UserDetail;
import xiaozhi.modules.appv2.dao.V2AccountDao;
import xiaozhi.modules.appv2.dao.V2DeviceBindingDao;
import xiaozhi.modules.appv2.dao.V2MemberDao;
import xiaozhi.modules.appv2.dto.V2MemberCreateRequest;
import xiaozhi.modules.appv2.dto.V2MemberResponse;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2MemberEntity;
import xiaozhi.modules.appv2.service.MemberService;
import xiaozhi.modules.security.user.SecurityUser;

@Service
@AllArgsConstructor
public class MemberServiceImpl implements MemberService {
    private static final String BINDING_STATUS_ACTIVE = "active";
    private static final String STATUS_ACTIVE = "active";
    private static final String ACCOUNT_STATUS_DELETED = "deleted";

    private final V2DeviceBindingDao v2DeviceBindingDao;
    private final V2MemberDao v2MemberDao;
    private final V2AccountDao v2AccountDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2MemberResponse create(V2MemberCreateRequest request) {
        UserDetail user = requireUser();
        String deviceId = required(request.getDeviceId());
        ensureActiveBinding(user.getId(), deviceId);
        String memberType = normalizeMemberType(request.getMemberType());

        V2MemberEntity member = new V2MemberEntity();
        member.setAccountId(user.getId());
        member.setDeviceId(deviceId);
        member.setDisplayName(required(request.getDisplayName()));
        member.setRole(memberType);
        member.setMemberType(memberType);
        member.setStatus(STATUS_ACTIVE);
        v2MemberDao.insert(member);
        return toResponse(member);
    }

    @Override
    public List<V2MemberResponse> listByDevice(String deviceId) {
        UserDetail user = requireUser();
        String normalizedDeviceId = required(deviceId);
        ensureActiveBinding(user.getId(), normalizedDeviceId);
        List<V2MemberEntity> members = v2MemberDao.selectList(new LambdaQueryWrapper<V2MemberEntity>()
                .eq(V2MemberEntity::getAccountId, user.getId())
                .eq(V2MemberEntity::getDeviceId, normalizedDeviceId)
                .eq(V2MemberEntity::getStatus, STATUS_ACTIVE)
                .orderByAsc(V2MemberEntity::getId));
        return members.stream().map(MemberServiceImpl::toResponse).toList();
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

    private static V2MemberResponse toResponse(V2MemberEntity member) {
        return new V2MemberResponse(
                member.getId(),
                member.getDeviceId(),
                member.getDisplayName(),
                member.getRole(),
                member.getMemberType(),
                member.getStatus());
    }

    private static String normalizeMemberType(String value) {
        String text = required(value).toLowerCase(Locale.ROOT);
        return switch (text) {
            case "owner", "member", "child" -> text;
            default -> throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        };
    }

    private static String required(String value) {
        String text = StringUtils.trimToNull(value);
        if (text == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        return text;
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
}
