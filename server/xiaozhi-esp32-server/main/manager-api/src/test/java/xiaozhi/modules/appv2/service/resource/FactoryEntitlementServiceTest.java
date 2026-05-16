package xiaozhi.modules.appv2.service.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.modules.appv2.dao.V2EntitlementDao;
import xiaozhi.modules.appv2.entity.V2EntitlementEntity;

@ExtendWith(MockitoExtension.class)
class FactoryEntitlementServiceTest {
    @Mock
    private V2EntitlementDao v2EntitlementDao;

    @Test
    void insertsFactoryFreeResourcesForNewAccount() {
        when(v2EntitlementDao.selectCount(any())).thenReturn(0L);
        FactoryEntitlementService service = new FactoryEntitlementService(v2EntitlementDao);

        service.ensureFactoryEntitlements(21L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<V2EntitlementEntity> captor = ArgumentCaptor.forClass(V2EntitlementEntity.class);
        verify(v2EntitlementDao, org.mockito.Mockito.times(8)).insert(captor.capture());
        assertEquals(8, captor.getAllValues().size());
        assertEntitlement(captor.getAllValues().get(0), 21L, "font", "kai_basic_v1");
        assertEntitlement(captor.getAllValues().get(1), 21L, "copybook", "pinyin_basic_v1");
        assertEntitlement(captor.getAllValues().get(2), 21L, "asset", "starter_star");
        assertEntitlement(captor.getAllValues().get(6), 21L, "asset", "starter_flower");
        assertEntitlement(captor.getAllValues().get(7), 21L, "ai_plan", "local_fake_ai_plan_v1");
    }

    @Test
    void skipsExistingFactoryResources() {
        when(v2EntitlementDao.selectCount(any())).thenReturn(1L);
        FactoryEntitlementService service = new FactoryEntitlementService(v2EntitlementDao);

        service.ensureFactoryEntitlements(21L);

        verify(v2EntitlementDao, org.mockito.Mockito.never()).insert(any());
    }

    private static void assertEntitlement(
            V2EntitlementEntity entitlement,
            Long accountId,
            String resourceType,
            String resourceId) {
        assertEquals(accountId, entitlement.getAccountId());
        assertEquals(resourceType, entitlement.getResourceType());
        assertEquals(resourceId, entitlement.getResourceId());
        assertEquals(FactoryEntitlementService.SOURCE_FACTORY, entitlement.getSource());
    }
}
