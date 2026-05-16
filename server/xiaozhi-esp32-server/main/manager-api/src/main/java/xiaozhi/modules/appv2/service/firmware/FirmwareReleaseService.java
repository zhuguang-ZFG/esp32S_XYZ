package xiaozhi.modules.appv2.service.firmware;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.AllArgsConstructor;
import xiaozhi.modules.appv2.dao.V2FirmwareReleaseDao;
import xiaozhi.modules.appv2.entity.V2FirmwareReleaseEntity;

@Service
@AllArgsConstructor
public class FirmwareReleaseService {
    public static final String CHANNEL_DEV = "dev";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_PAUSED = "paused";
    public static final int ROLLOUT_10 = 10;
    public static final int ROLLOUT_50 = 50;
    public static final int ROLLOUT_100 = 100;
    private static final Pattern LOWER_HEX_SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    private final V2FirmwareReleaseDao firmwareReleaseDao;

    public Optional<V2FirmwareReleaseEntity> findUpgradeForDevice(String channel, String deviceId, String currentVersion) {
        if (StringUtils.isBlank(deviceId)) {
            return Optional.empty();
        }
        String normalizedChannel = StringUtils.defaultIfBlank(channel, CHANNEL_DEV).trim();
        V2FirmwareReleaseEntity release = firmwareReleaseDao.selectOne(new LambdaQueryWrapper<V2FirmwareReleaseEntity>()
                .eq(V2FirmwareReleaseEntity::getChannel, normalizedChannel)
                .eq(V2FirmwareReleaseEntity::getStatus, STATUS_PUBLISHED)
                .orderByDesc(V2FirmwareReleaseEntity::getPublishedAt)
                .orderByDesc(V2FirmwareReleaseEntity::getReleaseId)
                .last("limit 1"));
        if (release == null || !isDeviceInRollout(deviceId, release.getRolloutPercent())) {
            return Optional.empty();
        }
        if (StringUtils.equals(currentVersion, release.getVersion())) {
            return Optional.empty();
        }
        return Optional.of(release);
    }

    @Transactional(rollbackFor = Exception.class)
    public V2FirmwareReleaseEntity publishDevRelease(String releaseId, String version, String url, String sha256, String signature,
            Integer rolloutPercent, Integer failureThresholdPercent) {
        V2FirmwareReleaseEntity release = new V2FirmwareReleaseEntity();
        release.setReleaseId(StringUtils.defaultIfBlank(releaseId, "dev-" + version));
        release.setChannel(CHANNEL_DEV);
        release.setVersion(required(version, "version"));
        release.setUrl(requireHttpsUrl(url));
        release.setSha256(requireLowerHexSha256(sha256));
        release.setSignature(requireBase64Signature(signature));
        release.setRolloutPercent(clampRollout(rolloutPercent));
        release.setFailureThresholdPercent(failureThresholdPercent == null ? 20 : failureThresholdPercent);
        release.setInstallCount(0);
        release.setFailureCount(0);
        release.setStatus(STATUS_PUBLISHED);
        release.setPublishedAt(new Date());
        if (firmwareReleaseDao.selectById(release.getReleaseId()) == null) {
            firmwareReleaseDao.insert(release);
        } else {
            firmwareReleaseDao.updateById(release);
        }
        return release;
    }

    public boolean isDeviceInRollout(String deviceId, Integer rolloutPercent) {
        int percent = clampRollout(rolloutPercent);
        if (percent <= 0 || StringUtils.isBlank(deviceId)) {
            return false;
        }
        if (percent >= ROLLOUT_100) {
            return true;
        }
        return deviceBucket(deviceId) < percent;
    }

    @Transactional(rollbackFor = Exception.class)
    public V2FirmwareReleaseEntity recordInstallResult(String releaseId, boolean success) {
        if (StringUtils.isBlank(releaseId)) {
            return null;
        }
        V2FirmwareReleaseEntity release = firmwareReleaseDao.selectById(releaseId);
        if (release == null) {
            return null;
        }

        int installs = valueOrZero(release.getInstallCount()) + 1;
        int failures = valueOrZero(release.getFailureCount()) + (success ? 0 : 1);
        release.setInstallCount(installs);
        release.setFailureCount(failures);
        if (shouldPauseForFailureRate(installs, failures, release.getFailureThresholdPercent())) {
            release.setStatus(STATUS_PAUSED);
        }
        firmwareReleaseDao.updateById(release);
        return release;
    }

    boolean shouldPauseForFailureRate(int installCount, int failureCount, Integer thresholdPercent) {
        if (installCount <= 0 || failureCount <= 0 || thresholdPercent == null || thresholdPercent <= 0) {
            return false;
        }
        return failureCount * 100 >= installCount * thresholdPercent;
    }

    private int deviceBucket(String deviceId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(deviceId.getBytes(StandardCharsets.UTF_8));
            int value = ((hash[0] & 0xff) << 8) | (hash[1] & 0xff);
            return value % 100;
        } catch (NoSuchAlgorithmException ex) {
            return Math.floorMod(deviceId.hashCode(), 100);
        }
    }

    private int clampRollout(Integer rolloutPercent) {
        if (rolloutPercent == null) {
            return 0;
        }
        if (rolloutPercent <= ROLLOUT_10) {
            return ROLLOUT_10;
        }
        if (rolloutPercent <= ROLLOUT_50) {
            return ROLLOUT_50;
        }
        return ROLLOUT_100;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String required(String value, String name) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private String requireHttpsUrl(String value) {
        String url = required(value, "url");
        if (!url.startsWith("https://")) {
            throw new IllegalArgumentException("url must use https");
        }
        return url;
    }

    private String requireLowerHexSha256(String value) {
        String sha256 = required(value, "sha256");
        if (!LOWER_HEX_SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hex chars");
        }
        return sha256;
    }

    private String requireBase64Signature(String value) {
        String signature = required(value, "signature");
        try {
            if (Base64.getDecoder().decode(signature).length == 0) {
                throw new IllegalArgumentException("signature must be base64");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("signature must be base64", e);
        }
        return signature;
    }
}
