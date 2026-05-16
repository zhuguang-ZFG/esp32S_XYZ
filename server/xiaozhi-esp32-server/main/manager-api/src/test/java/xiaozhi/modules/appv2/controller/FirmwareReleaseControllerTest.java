package xiaozhi.modules.appv2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.common.utils.Result;
import xiaozhi.modules.appv2.dto.V2FirmwareInstallResultRequest;
import xiaozhi.modules.appv2.dto.V2FirmwareReleasePublishRequest;
import xiaozhi.modules.appv2.dto.V2FirmwareReleaseResponse;
import xiaozhi.modules.appv2.entity.V2FirmwareReleaseEntity;
import xiaozhi.modules.appv2.service.firmware.FirmwareReleaseService;

@ExtendWith(MockitoExtension.class)
class FirmwareReleaseControllerTest {
    @Mock
    private FirmwareReleaseService firmwareReleaseService;

    @Test
    void firmwareReleaseEndpointsDelegateToReleaseService() {
        FirmwareReleaseController controller = new FirmwareReleaseController(firmwareReleaseService);
        V2FirmwareReleasePublishRequest publishRequest = new V2FirmwareReleasePublishRequest();
        publishRequest.setReleaseId("rel-1");
        publishRequest.setVersion("1.2.3");
        publishRequest.setUrl("https://ota.example.com/u8.bin");
        publishRequest.setSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        publishRequest.setSignature("c2ln");
        publishRequest.setRolloutPercent(10);
        publishRequest.setFailureThresholdPercent(20);
        when(firmwareReleaseService.publishDevRelease(
                "rel-1",
                "1.2.3",
                "https://ota.example.com/u8.bin",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "c2ln",
                10,
                20)).thenReturn(release("rel-1", "published"));

        Result<V2FirmwareReleaseResponse> publishResponse = controller.publishFirmwareRelease(publishRequest);

        assertEquals(0, publishResponse.getCode());
        assertEquals("rel-1", publishResponse.getData().getReleaseId());
        assertEquals("published", publishResponse.getData().getStatus());
        verify(firmwareReleaseService).publishDevRelease(
                "rel-1",
                "1.2.3",
                "https://ota.example.com/u8.bin",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "c2ln",
                10,
                20);

        V2FirmwareInstallResultRequest resultRequest = new V2FirmwareInstallResultRequest();
        resultRequest.setSuccess(false);
        when(firmwareReleaseService.recordInstallResult("rel-1", false)).thenReturn(release("rel-1", "paused"));

        Result<V2FirmwareReleaseResponse> installResponse =
                controller.recordFirmwareInstallResult("rel-1", resultRequest);

        assertEquals(0, installResponse.getCode());
        assertEquals("paused", installResponse.getData().getStatus());
        verify(firmwareReleaseService).recordInstallResult("rel-1", false);
    }

    private static V2FirmwareReleaseEntity release(String releaseId, String status) {
        V2FirmwareReleaseEntity release = new V2FirmwareReleaseEntity();
        release.setReleaseId(releaseId);
        release.setChannel("dev");
        release.setVersion("1.2.3");
        release.setUrl("https://ota.example.com/u8.bin");
        release.setSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        release.setSignature("c2ln");
        release.setRolloutPercent(10);
        release.setFailureThresholdPercent(20);
        release.setInstallCount(1);
        release.setFailureCount("paused".equals(status) ? 1 : 0);
        release.setStatus(status);
        return release;
    }
}
