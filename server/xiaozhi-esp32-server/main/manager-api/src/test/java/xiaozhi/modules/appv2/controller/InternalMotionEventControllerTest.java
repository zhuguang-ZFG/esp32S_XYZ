package xiaozhi.modules.appv2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.appv2.config.V2DeviceServerProperties;
import xiaozhi.modules.appv2.service.AppV2Service;

@ExtendWith(MockitoExtension.class)
class InternalMotionEventControllerTest {

    @Mock
    private V2DeviceServerProperties properties;
    @Mock
    private AppV2Service appV2Service;

    @Test
    void rejectsMissingBearerToken() {
        when(properties.getInternalToken()).thenReturn("secret-token");
        InternalMotionEventController controller = new InternalMotionEventController(properties, appV2Service);

        Result<Void> response = controller.motionEvent(null, payload());

        assertEquals(ErrorCode.UNAUTHORIZED, response.getCode());
        assertEquals("missing bearer token", response.getMsg());
    }

    @Test
    void rejectsWrongBearerToken() {
        when(properties.getInternalToken()).thenReturn("secret-token");
        InternalMotionEventController controller = new InternalMotionEventController(properties, appV2Service);

        Result<Void> response = controller.motionEvent("Bearer wrong-token", payload());

        assertEquals(ErrorCode.UNAUTHORIZED, response.getCode());
        assertEquals("invalid token", response.getMsg());
    }

    @Test
    void forwardsMotionEventWhenTokenMatches() {
        when(properties.getInternalToken()).thenReturn("secret-token");
        InternalMotionEventController controller = new InternalMotionEventController(properties, appV2Service);
        Map<String, Object> payload = payload();

        Result<Void> response = controller.motionEvent("Bearer secret-token", payload);

        assertEquals(0, response.getCode());
        assertEquals("success", response.getMsg());
        assertNull(response.getData());
        verify(appV2Service).ingestMotionEvent(payload);
    }

    private static Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", "task-1");
        payload.put("device_id", "dev-1");
        payload.put("phase", "running");
        payload.put("capability", "home");
        return payload;
    }
}
