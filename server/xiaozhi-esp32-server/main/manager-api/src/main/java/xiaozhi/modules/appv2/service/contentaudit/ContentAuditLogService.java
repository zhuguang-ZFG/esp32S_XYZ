package xiaozhi.modules.appv2.service.contentaudit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.AllArgsConstructor;
import xiaozhi.modules.appv2.dao.V2ContentAuditDao;
import xiaozhi.modules.appv2.entity.V2ContentAuditEntity;

@Service
@AllArgsConstructor
public class ContentAuditLogService {
    private static final int MAX_PATH_LENGTH = 64;
    private static final int MAX_RULE_HIT_LENGTH = 255;
    private static final int RETENTION_DAYS = 180;

    private final V2ContentAuditDao v2ContentAuditDao;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBlockedContent(
            Long accountId,
            String deviceId,
            String path,
            String raw,
            String ruleHit) {
        V2ContentAuditEntity audit = new V2ContentAuditEntity();
        audit.setAccountId(accountId);
        audit.setDeviceId(deviceId);
        audit.setPath(StringUtils.left(StringUtils.defaultIfBlank(path, "unknown"), MAX_PATH_LENGTH));
        audit.setRawHash(sha256Hex(StringUtils.defaultString(raw)));
        audit.setRuleHit(StringUtils.left(StringUtils.defaultString(ruleHit), MAX_RULE_HIT_LENGTH));
        audit.setTs(new Date());
        v2ContentAuditDao.insert(audit);
    }

    @Transactional(rollbackFor = Exception.class)
    public int purgeExpired(Date now) {
        Objects.requireNonNull(now, "now");
        Date cutoff = Date.from(now.toInstant().minus(RETENTION_DAYS, ChronoUnit.DAYS));
        return v2ContentAuditDao.delete(new LambdaQueryWrapper<V2ContentAuditEntity>()
                .lt(V2ContentAuditEntity::getTs, cutoff));
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
