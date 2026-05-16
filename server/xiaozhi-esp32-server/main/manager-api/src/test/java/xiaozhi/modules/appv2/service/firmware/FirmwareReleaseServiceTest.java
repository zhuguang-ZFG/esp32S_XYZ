package xiaozhi.modules.appv2.service.firmware;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.modules.appv2.dao.V2FirmwareReleaseDao;
import xiaozhi.modules.appv2.entity.V2FirmwareReleaseEntity;

@ExtendWith(MockitoExtension.class)
class FirmwareReleaseServiceTest {
    private static final String SHA256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String SIGNATURE = Base64.getEncoder().encodeToString("signature".getBytes());

    @Mock
    private V2FirmwareReleaseDao firmwareReleaseDao;

    @Test
    void publishDevReleaseRequiresM54VerifiableMetadata() {
        FirmwareReleaseService service = new FirmwareReleaseService(firmwareReleaseDao);
        when(firmwareReleaseDao.selectById("rel-1")).thenReturn(null);

        V2FirmwareReleaseEntity release = service.publishDevRelease(
                "rel-1",
                "1.2.3",
                "https://ota.example.com/u8.bin",
                SHA256,
                SIGNATURE,
                10,
                20);

        assertEquals("https://ota.example.com/u8.bin", release.getUrl());
        assertEquals(SHA256, release.getSha256());
        assertEquals(SIGNATURE, release.getSignature());
        ArgumentCaptor<V2FirmwareReleaseEntity> captor = ArgumentCaptor.forClass(V2FirmwareReleaseEntity.class);
        verify(firmwareReleaseDao).insert(captor.capture());
        assertEquals(FirmwareReleaseService.STATUS_PUBLISHED, captor.getValue().getStatus());
    }

    @Test
    void publishDevReleaseRejectsMetadataThatClientCannotVerify() {
        FirmwareReleaseService service = new FirmwareReleaseService(firmwareReleaseDao);

        assertThrows(IllegalArgumentException.class, () -> service.publishDevRelease(
                "rel-http", "1.2.3", "http://ota.example.com/u8.bin", SHA256, SIGNATURE, 10, 20));
        assertThrows(IllegalArgumentException.class, () -> service.publishDevRelease(
                "rel-sha", "1.2.3", "https://ota.example.com/u8.bin", SHA256.toUpperCase(), SIGNATURE, 10, 20));
        assertThrows(IllegalArgumentException.class, () -> service.publishDevRelease(
                "rel-sig", "1.2.3", "https://ota.example.com/u8.bin", SHA256, "not base64!!", 10, 20));
    }
}
