package xiaozhi.modules.appv2.service.contentaudit;

import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class ContentAuditService {
    private static final List<String> BLOCKED_KEYWORDS = List.of(
            "不当文字",
            "违规内容",
            "暴力",
            "色情",
            "赌博",
            "毒品");

    public ContentAuditDecision auditInboundText(String text) {
        String normalized = StringUtils.defaultString(text).trim().toLowerCase(Locale.ROOT);
        if (StringUtils.isBlank(normalized)) {
            return ContentAuditDecision.allow();
        }
        for (String keyword : BLOCKED_KEYWORDS) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return ContentAuditDecision.block("keyword:" + keyword);
            }
        }
        return ContentAuditDecision.allow();
    }

    public void requireInboundTextAllowed(String text) {
        ContentAuditDecision decision = auditInboundText(text);
        if (!decision.isAllowed()) {
            throw new ContentAuditException(decision.getRuleHit());
        }
    }
}
