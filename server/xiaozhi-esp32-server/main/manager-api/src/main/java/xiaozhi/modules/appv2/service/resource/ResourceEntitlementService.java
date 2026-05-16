package xiaozhi.modules.appv2.service.resource;

import java.util.Date;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.AllArgsConstructor;
import xiaozhi.modules.appv2.dao.V2EntitlementDao;
import xiaozhi.modules.appv2.entity.V2EntitlementEntity;
import xiaozhi.modules.appv2.service.projection.WriteTextProjectionService;

@Service
@AllArgsConstructor
public class ResourceEntitlementService {
    public static final String DEFAULT_DRAW_GENERATED_AI_PLAN_ID = "local_fake_ai_plan_v1";

    private static final String CAPABILITY_WRITE_TEXT = "write_text";
    private static final String CAPABILITY_DRAW_GENERATED = "draw_generated";

    private final V2EntitlementDao v2EntitlementDao;

    public void requireSubmitEntitlements(Long accountId, String capability, Map<String, Object> params) {
        if (StringUtils.isBlank(capability)) {
            return;
        }

        String normalizedCapability = capability.trim();
        if (CAPABILITY_WRITE_TEXT.equals(normalizedCapability)) {
            String fontId = stringParam(params, "font_id", WriteTextProjectionService.DEFAULT_FONT_ID);
            requireEntitlement(accountId, "font", fontId);
            return;
        }

        if (CAPABILITY_DRAW_GENERATED.equals(normalizedCapability)) {
            String starterAssetId = firstStringParam(params, "starter_id", "starter_asset_id", "preset_id");
            if (StringUtils.isNotBlank(starterAssetId) && booleanParam(params, "use_starter_asset")) {
                requireEntitlement(accountId, "asset", starterAssetId);
                return;
            }
            if (requiresAiPlan(params)) {
                requireEntitlement(accountId, "ai_plan", DEFAULT_DRAW_GENERATED_AI_PLAN_ID);
            }
        }
    }

    private static boolean requiresAiPlan(Map<String, Object> params) {
        if (params == null) {
            return false;
        }
        return StringUtils.isNotBlank(stringValue(params.get("prompt")))
                && StringUtils.isBlank(stringValue(params.get("svg_text")))
                && StringUtils.isBlank(firstStringParam(params, "bitmap_base64", "bitmap_data_uri", "image_base64"));
    }

    private void requireEntitlement(Long accountId, String resourceType, String resourceId) {
        if (accountId == null || StringUtils.isBlank(resourceId) || !hasActiveEntitlement(accountId, resourceType, resourceId)) {
            throw new EntitlementValidationException(resourceType, resourceId);
        }
    }

    private boolean hasActiveEntitlement(Long accountId, String resourceType, String resourceId) {
        V2EntitlementEntity entitlement = v2EntitlementDao.selectOne(new LambdaQueryWrapper<V2EntitlementEntity>()
                .eq(V2EntitlementEntity::getAccountId, accountId)
                .eq(V2EntitlementEntity::getResourceType, resourceType)
                .eq(V2EntitlementEntity::getResourceId, resourceId)
                .and(wrapper -> wrapper
                        .isNull(V2EntitlementEntity::getExpiresAt)
                        .or()
                        .gt(V2EntitlementEntity::getExpiresAt, new Date()))
                .last("limit 1"));
        return entitlement != null;
    }

    private static String firstStringParam(Map<String, Object> params, String... keys) {
        if (params == null) {
            return null;
        }
        for (String key : keys) {
            String value = stringValue(params.get(key));
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String stringParam(Map<String, Object> params, String key, String defaultValue) {
        String value = params == null ? null : stringValue(params.get(key));
        return StringUtils.defaultIfBlank(value, defaultValue);
    }

    private static boolean booleanParam(Map<String, Object> params, String key) {
        if (params == null) {
            return false;
        }
        Object value = params.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(stringValue(value));
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.isBlank(text) ? null : text;
    }
}
