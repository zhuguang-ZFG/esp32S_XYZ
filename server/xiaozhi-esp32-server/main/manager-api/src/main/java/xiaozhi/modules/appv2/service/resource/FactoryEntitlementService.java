package xiaozhi.modules.appv2.service.resource;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.AllArgsConstructor;
import xiaozhi.modules.appv2.dao.V2EntitlementDao;
import xiaozhi.modules.appv2.entity.V2EntitlementEntity;

@Service
@AllArgsConstructor
public class FactoryEntitlementService {
    public static final String SOURCE_FACTORY = "factory";

    private static final List<FactoryResource> FACTORY_RESOURCES = List.of(
            new FactoryResource("font", "kai_basic_v1"),
            new FactoryResource("copybook", "pinyin_basic_v1"),
            new FactoryResource("asset", "starter_star"),
            new FactoryResource("asset", "starter_house"),
            new FactoryResource("asset", "starter_tree"),
            new FactoryResource("asset", "starter_fish"),
            new FactoryResource("asset", "starter_flower"),
            new FactoryResource("ai_plan", ResourceEntitlementService.DEFAULT_DRAW_GENERATED_AI_PLAN_ID));

    private final V2EntitlementDao v2EntitlementDao;

    @Transactional(rollbackFor = Exception.class)
    public void ensureFactoryEntitlements(Long accountId) {
        if (accountId == null) {
            return;
        }
        for (FactoryResource resource : FACTORY_RESOURCES) {
            if (exists(accountId, resource)) {
                continue;
            }
            V2EntitlementEntity entitlement = new V2EntitlementEntity();
            entitlement.setAccountId(accountId);
            entitlement.setResourceType(resource.type());
            entitlement.setResourceId(resource.id());
            entitlement.setSource(SOURCE_FACTORY);
            v2EntitlementDao.insert(entitlement);
        }
    }

    private boolean exists(Long accountId, FactoryResource resource) {
        Long count = v2EntitlementDao.selectCount(new LambdaQueryWrapper<V2EntitlementEntity>()
                .eq(V2EntitlementEntity::getAccountId, accountId)
                .eq(V2EntitlementEntity::getResourceType, resource.type())
                .eq(V2EntitlementEntity::getResourceId, resource.id()));
        return count != null && count > 0L;
    }

    private record FactoryResource(String type, String id) {}
}
