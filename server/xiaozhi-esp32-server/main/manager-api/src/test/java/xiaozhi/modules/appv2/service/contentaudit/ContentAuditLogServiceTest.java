package xiaozhi.modules.appv2.service.contentaudit;

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

import xiaozhi.modules.appv2.dao.V2ContentAuditDao;
import xiaozhi.modules.appv2.entity.V2ContentAuditEntity;

@ExtendWith(MockitoExtension.class)
class ContentAuditLogServiceTest {
    @Mock
    private V2ContentAuditDao v2ContentAuditDao;

    @Test
    void recordsBlockedContentMetadataWithoutRawText() {
        ContentAuditLogService service = new ContentAuditLogService(v2ContentAuditDao);

        service.recordBlockedContent(31L, "dev-1", "write_text.text", "这是一段不当文字", "keyword:不当文字");

        ArgumentCaptor<V2ContentAuditEntity> captor = ArgumentCaptor.forClass(V2ContentAuditEntity.class);
        verify(v2ContentAuditDao).insert(captor.capture());
        V2ContentAuditEntity audit = captor.getValue();
        assertEquals(31L, audit.getAccountId());
        assertEquals("dev-1", audit.getDeviceId());
        assertEquals("write_text.text", audit.getPath());
        assertEquals("keyword:不当文字", audit.getRuleHit());
        assertEquals("b40ec83b4df86bbb4643a964cf8d5a5591338ecc49f330653f7828ea5be906dc", audit.getRawHash());
        assertNotNull(audit.getTs());
    }

    @Test
    void purgesAuditOlderThanRetentionWindow() {
        ContentAuditLogService service = new ContentAuditLogService(v2ContentAuditDao);
        when(v2ContentAuditDao.delete(any())).thenReturn(2);

        int deleted = service.purgeExpired(Date.from(Instant.parse("2026-05-15T00:00:00Z")));

        assertEquals(2, deleted);
        verify(v2ContentAuditDao).delete(any());
    }
}
