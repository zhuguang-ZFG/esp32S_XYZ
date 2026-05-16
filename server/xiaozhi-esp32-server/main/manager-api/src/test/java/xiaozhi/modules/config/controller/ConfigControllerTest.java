package xiaozhi.modules.config.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.common.utils.Result;
import xiaozhi.modules.config.dto.DeviceRuntimeStatusDTO;
import xiaozhi.modules.config.service.ConfigService;
import xiaozhi.modules.config.vo.DeviceRuntimeStatusVO;

@ExtendWith(MockitoExtension.class)
class ConfigControllerTest {
    @Mock
    private ConfigService configService;

    @Test
    void deviceRuntimeStatusDelegatesToConfigService() {
        ConfigController controller = new ConfigController(configService);
        DeviceRuntimeStatusDTO request = new DeviceRuntimeStatusDTO();
        request.setDeviceId("dev-1");
        when(configService.getDeviceRuntimeStatus("dev-1"))
                .thenReturn(new DeviceRuntimeStatusVO("dev-1", "disposed", true, true));

        Result<DeviceRuntimeStatusVO> response = controller.getDeviceRuntimeStatus(request);

        assertEquals(0, response.getCode());
        assertEquals("dev-1", response.getData().getDeviceId());
        assertTrue(response.getData().isKnown());
        assertTrue(response.getData().isDisposed());
        verify(configService).getDeviceRuntimeStatus("dev-1");
    }

    @Test
    void deviceRuntimeStatusCanRepresentUnknownDevices() {
        DeviceRuntimeStatusVO status = new DeviceRuntimeStatusVO("dev-unknown", null, false, false);

        assertEquals("dev-unknown", status.getDeviceId());
        assertFalse(status.isKnown());
        assertFalse(status.isDisposed());
    }
}
