package xiaozhi.modules.appv2.service;

import java.util.Date;

import xiaozhi.modules.appv2.dto.V2DeletionResponse;

public interface PrivacyDeletionService {
    V2DeletionResponse deleteVoiceprint(Long voiceprintId);

    V2DeletionResponse deleteAccount();

    int purgeExpiredRetention(Date now);
}
