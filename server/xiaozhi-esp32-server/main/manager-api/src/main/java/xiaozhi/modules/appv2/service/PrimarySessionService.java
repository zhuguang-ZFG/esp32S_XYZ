package xiaozhi.modules.appv2.service;

public interface PrimarySessionService {
    void requirePrimaryForWrite(Long accountId, String deviceId, String sessionId);

    void requireVoiceAllowedForWrite(Long accountId, String deviceId);

    void claimPrimary(Long accountId, String deviceId, String sessionId);
}
