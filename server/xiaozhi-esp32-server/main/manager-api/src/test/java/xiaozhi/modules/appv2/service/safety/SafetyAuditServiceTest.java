package xiaozhi.modules.appv2.service.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.modules.appv2.dao.V2SafetyAuditDao;
import xiaozhi.modules.appv2.entity.V2SafetyAuditEntity;

@ExtendWith(MockitoExtension.class)
class SafetyAuditServiceTest {
    @Mock
    private V2SafetyAuditDao v2SafetyAuditDao;

    @Test
    void recordsBusinessSafetyRejection() {
        SafetyAuditService service = new SafetyAuditService(v2SafetyAuditDao);
        SafetyValidationException error =
                new SafetyValidationException(SafetyErrorCode.E_OUT_OF_RANGE, "path bounds exceed writable area");

        service.recordBusinessReject(31L, "dev-1", "run_path", error);

        ArgumentCaptor<V2SafetyAuditEntity> captor = ArgumentCaptor.forClass(V2SafetyAuditEntity.class);
        verify(v2SafetyAuditDao).insert(captor.capture());
        V2SafetyAuditEntity audit = captor.getValue();
        assertEquals(31L, audit.getAccountId());
        assertEquals("dev-1", audit.getDeviceId());
        assertEquals("run_path", audit.getCapability());
        assertEquals("E_OUT_OF_RANGE:path bounds exceed writable area", audit.getReason());
        assertNotNull(audit.getTs());
    }

    @Test
    void recordsU1SafetyRejection() {
        SafetyAuditService service = new SafetyAuditService(v2SafetyAuditDao);

        service.recordU1Reject(31L, "dev-1", "run_path", "E002", "soft limit exceeded");

        ArgumentCaptor<V2SafetyAuditEntity> captor = ArgumentCaptor.forClass(V2SafetyAuditEntity.class);
        verify(v2SafetyAuditDao).insert(captor.capture());
        V2SafetyAuditEntity audit = captor.getValue();
        assertEquals(31L, audit.getAccountId());
        assertEquals("dev-1", audit.getDeviceId());
        assertEquals("run_path", audit.getCapability());
        assertEquals("U1:E002:soft limit exceeded", audit.getReason());
        assertNotNull(audit.getTs());
    }

    @Test
    void purgesAuditOlderThanRetentionWindow() {
        SafetyAuditService service = new SafetyAuditService(v2SafetyAuditDao);
        when(v2SafetyAuditDao.delete(any())).thenReturn(3);

        int deleted = service.purgeExpired(Date.from(Instant.parse("2026-05-15T00:00:00Z")));

        assertEquals(3, deleted);
        verify(v2SafetyAuditDao).delete(any());
    }
}
