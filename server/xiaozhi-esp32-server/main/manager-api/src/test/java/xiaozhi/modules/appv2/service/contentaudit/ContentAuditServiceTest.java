package xiaozhi.modules.appv2.service.contentaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContentAuditServiceTest {
    private final ContentAuditService service = new ContentAuditService();

    @Test
    void allowsNormalInboundText() {
        ContentAuditDecision decision = service.auditInboundText("你好");

        assertTrue(decision.isAllowed());
    }

    @Test
    void blocksConfiguredKeyword() {
        ContentAuditException error = assertThrows(
                ContentAuditException.class,
                () -> service.requireInboundTextAllowed("这是一段不当文字"));

        assertEquals("E_CONTENT_BLOCKED", error.getErrorCode());
        assertEquals("keyword:不当文字", error.getRuleHit());
    }
}
