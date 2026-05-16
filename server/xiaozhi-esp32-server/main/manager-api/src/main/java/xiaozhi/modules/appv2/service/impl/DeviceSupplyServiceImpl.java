package xiaozhi.modules.appv2.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.json.JSONUtil;
import lombok.AllArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.user.UserDetail;
import xiaozhi.modules.appv2.dao.V2AccountDao;
import xiaozhi.modules.appv2.dao.V2DeviceBindingDao;
import xiaozhi.modules.appv2.dao.V2DeviceSupplyDao;
import xiaozhi.modules.appv2.dto.V2DeviceSupplyResponse;
import xiaozhi.modules.appv2.dto.V2DeviceSupplyUpdateRequest;
import xiaozhi.modules.appv2.entity.V2AccountEntity;
import xiaozhi.modules.appv2.entity.V2DeviceBindingEntity;
import xiaozhi.modules.appv2.entity.V2DeviceSupplyEntity;
import xiaozhi.modules.appv2.service.DeviceSupplyService;
import xiaozhi.modules.appv2.service.safety.SafetyErrorCode;
import xiaozhi.modules.appv2.service.safety.SafetyValidationException;
import xiaozhi.modules.security.user.SecurityUser;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@Service
@AllArgsConstructor
public class DeviceSupplyServiceImpl implements DeviceSupplyService {
    private static final String BINDING_STATUS_ACTIVE = "active";
    private static final String PAPER_EMPTY = "empty";
    private static final String PAPER_LOADED = "loaded";
    private static final String PAPER_UNKNOWN = "unknown";
    private static final String ACCOUNT_STATUS_DELETED = "deleted";

    private final V2DeviceSupplyDao v2DeviceSupplyDao;
    private final V2DeviceBindingDao v2DeviceBindingDao;
    private final V2AccountDao v2AccountDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public V2DeviceSupplyResponse updateSupplies(String deviceId, V2DeviceSupplyUpdateRequest request) {
        UserDetail user = requireUser();
        String normalizedDeviceId = required(deviceId);
        ensureActiveBinding(user.getId(), normalizedDeviceId);

        V2DeviceSupplyEntity supply = v2DeviceSupplyDao.selectById(normalizedDeviceId);
        boolean insert = false;
        if (supply == null) {
            supply = new V2DeviceSupplyEntity();
            supply.setDeviceId(normalizedDeviceId);
            supply.setPaperSlotState(PAPER_UNKNOWN);
            supply.setPenMileageMm(BigDecimal.ZERO);
            insert = true;
        }

        if (request.getPaperSlotState() != null) {
            supply.setPaperSlotState(normalizePaperSlotState(request.getPaperSlotState()));
        }
        if (request.getPenInstalledAt() != null) {
            supply.setPenInstalledAt(request.getPenInstalledAt());
        }
        if (request.getPenInkPercentEst() != null) {
            supply.setPenInkPercentEst(normalizeInkPercent(request.getPenInkPercentEst()));
        }
        if (Boolean.TRUE.equals(request.getResetPenMileage())) {
            supply.setPenMileageMm(BigDecimal.ZERO);
            if (supply.getPenInstalledAt() == null) {
                supply.setPenInstalledAt(new Date());
            }
        }

        if (insert) {
            v2DeviceSupplyDao.insert(supply);
        } else {
            v2DeviceSupplyDao.updateById(supply);
        }
        return toResponse(supply);
    }

    @Override
    public void requirePaperReadyForWrite(String deviceId, String capability) {
        if (!requiresPaper(capability)) {
            return;
        }
        V2DeviceSupplyEntity supply = v2DeviceSupplyDao.selectById(required(deviceId));
        if (supply != null && PAPER_EMPTY.equalsIgnoreCase(supply.getPaperSlotState())) {
            throw new SafetyValidationException(SafetyErrorCode.E_NO_PAPER, "paper slot is marked empty");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordCompletedRunPathMileage(String deviceId, String paramsJson) {
        String normalizedDeviceId = required(deviceId);
        BigDecimal mileageMm = calculateRunPathMileageMm(paramsJson);
        if (mileageMm.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        V2DeviceSupplyEntity supply = v2DeviceSupplyDao.selectById(normalizedDeviceId);
        boolean insert = false;
        if (supply == null) {
            supply = new V2DeviceSupplyEntity();
            supply.setDeviceId(normalizedDeviceId);
            supply.setPaperSlotState(PAPER_UNKNOWN);
            supply.setPenMileageMm(BigDecimal.ZERO);
            insert = true;
        }

        BigDecimal current = supply.getPenMileageMm() == null ? BigDecimal.ZERO : supply.getPenMileageMm();
        supply.setPenMileageMm(current.add(mileageMm));
        if (insert) {
            v2DeviceSupplyDao.insert(supply);
        } else {
            v2DeviceSupplyDao.updateById(supply);
        }
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

    private static boolean requiresPaper(String capability) {
        String value = StringUtils.trimToEmpty(capability).toLowerCase(Locale.ROOT);
        return "write_text".equals(value) || "draw".equals(value) || "draw_generated".equals(value);
    }

    @SuppressWarnings("unchecked")
    private static BigDecimal calculateRunPathMileageMm(String paramsJson) {
        if (StringUtils.isBlank(paramsJson)) {
            return BigDecimal.ZERO;
        }
        Map<String, Object> params;
        try {
            params = JSONUtil.toBean(paramsJson, Map.class);
        } catch (RuntimeException e) {
            return BigDecimal.ZERO;
        }
        Object rawPath = params.get("path");
        if (!(rawPath instanceof List<?> path) || path.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Double previousX = null;
        Double previousY = null;
        Double previousZ = null;
        double total = 0.0;
        for (Object item : path) {
            if (!(item instanceof Map<?, ?> point)) {
                continue;
            }
            Double x = doubleValue(point.get("x"));
            Double y = doubleValue(point.get("y"));
            Double z = doubleValue(point.get("z"));
            if (x == null || y == null) {
                continue;
            }
            String cmd = StringUtils.trimToEmpty(String.valueOf(point.get("cmd"))).toUpperCase(Locale.ROOT);
            if ("L".equals(cmd) && previousX != null && previousY != null) {
                double dz = z == null || previousZ == null ? 0.0 : z - previousZ;
                total += Math.sqrt(Math.pow(x - previousX, 2) + Math.pow(y - previousY, 2) + Math.pow(dz, 2));
            }
            previousX = x;
            previousY = y;
            previousZ = z;
        }
        if (!Double.isFinite(total) || total <= 0.0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(total);
    }

    private static Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizePaperSlotState(String value) {
        String normalized = required(value).toLowerCase(Locale.ROOT);
        if (PAPER_EMPTY.equals(normalized) || PAPER_LOADED.equals(normalized) || PAPER_UNKNOWN.equals(normalized)) {
            return normalized;
        }
        throw new RenException(ErrorCode.PARAMS_GET_ERROR);
    }

    private static Integer normalizeInkPercent(Integer value) {
        if (value < 0 || value > 100) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        return value;
    }

    private static V2DeviceSupplyResponse toResponse(V2DeviceSupplyEntity supply) {
        return new V2DeviceSupplyResponse(
                supply.getDeviceId(),
                supply.getPaperSlotState(),
                supply.getPenInstalledAt(),
                supply.getPenInkPercentEst(),
                supply.getPenMileageMm());
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
