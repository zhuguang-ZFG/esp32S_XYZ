package xiaozhi.modules.appv2.service.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.modules.appv2.dao.V2EntitlementDao;
import xiaozhi.modules.appv2.entity.V2EntitlementEntity;

@ExtendWith(MockitoExtension.class)
class ResourceEntitlementServiceTest {
    @Mock
    private V2EntitlementDao v2EntitlementDao;

    @Test
    void allowsWriteTextWhenDefaultFontEntitlementExists() {
        when(v2EntitlementDao.selectOne(any())).thenReturn(entitlement("font", "kai_basic_v1"));
        ResourceEntitlementService service = new ResourceEntitlementService(v2EntitlementDao);

        service.requireSubmitEntitlements(31L, "write_text", Map.of("text", "hello"));

        verify(v2EntitlementDao).selectOne(any());
    }

    @Test
    void rejectsWriteTextWhenFontEntitlementIsMissing() {
        when(v2EntitlementDao.selectOne(any())).thenReturn(null);
        ResourceEntitlementService service = new ResourceEntitlementService(v2EntitlementDao);

        EntitlementValidationException error = assertThrows(
                EntitlementValidationException.class,
                () -> service.requireSubmitEntitlements(31L, "write_text", Map.of("font_id", "kai_premium_v1")));

        assertEquals("E_NOT_ENTITLED", error.getErrorCode());
        assertEquals("font", error.getResourceType());
        assertEquals("kai_premium_v1", error.getResourceId());
    }

    @Test
    void rejectsWriteTextWhenOnlyExpiredFontEntitlementExists() {
        when(v2EntitlementDao.selectOne(any())).thenReturn(null);
        ResourceEntitlementService service = new ResourceEntitlementService(v2EntitlementDao);

        EntitlementValidationException error = assertThrows(
                EntitlementValidationException.class,
                () -> service.requireSubmitEntitlements(31L, "write_text", Map.of("font_id", "kai_expired_v1")));

        assertEquals("E_NOT_ENTITLED", error.getErrorCode());
        assertEquals("font", error.getResourceType());
        assertEquals("kai_expired_v1", error.getResourceId());
    }

    @Test
    void drawGeneratedPromptRequiresAiPlanEntitlement() {
        when(v2EntitlementDao.selectOne(any())).thenReturn(null);
        ResourceEntitlementService service = new ResourceEntitlementService(v2EntitlementDao);

        EntitlementValidationException error = assertThrows(
                EntitlementValidationException.class,
                () -> service.requireSubmitEntitlements(31L, "draw_generated", Map.of("prompt", "star")));

        assertEquals("E_NOT_ENTITLED", error.getErrorCode());
        assertEquals("ai_plan", error.getResourceType());
        assertEquals("local_fake_ai_plan_v1", error.getResourceId());
    }

    @Test
    void drawGeneratedSvgTextDoesNotRequireAiPlanEntitlement() {
        ResourceEntitlementService service = new ResourceEntitlementService(v2EntitlementDao);

        service.requireSubmitEntitlements(31L, "draw_generated", Map.of(
                "prompt", "star",
                "svg_text", "<svg viewBox=\"0 0 10 10\"><path d=\"M1 1 L9 9\" fill=\"none\" stroke=\"black\"/></svg>"));

        verify(v2EntitlementDao, never()).selectOne(any());
    }

    @Test
    void drawGeneratedExplicitStarterRequiresAssetEntitlement() {
        when(v2EntitlementDao.selectOne(any())).thenReturn(null);
        ResourceEntitlementService service = new ResourceEntitlementService(v2EntitlementDao);

        EntitlementValidationException error = assertThrows(
                EntitlementValidationException.class,
                () -> service.requireSubmitEntitlements(31L, "draw_generated", Map.of(
                        "starter_id", "starter_star",
                        "use_starter_asset", true)));

        assertEquals("E_NOT_ENTITLED", error.getErrorCode());
        assertEquals("asset", error.getResourceType());
        assertEquals("starter_star", error.getResourceId());
    }

    private static V2EntitlementEntity entitlement(String resourceType, String resourceId) {
        V2EntitlementEntity entitlement = new V2EntitlementEntity();
        entitlement.setAccountId(31L);
        entitlement.setResourceType(resourceType);
        entitlement.setResourceId(resourceId);
        return entitlement;
    }
}
