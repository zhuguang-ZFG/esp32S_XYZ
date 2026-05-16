package xiaozhi.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import xiaozhi.common.utils.Result;
import xiaozhi.modules.appv2.service.PrimarySessionException;
import xiaozhi.modules.appv2.service.resource.EntitlementValidationException;
import xiaozhi.modules.appv2.service.safety.SafetyErrorCode;
import xiaozhi.modules.appv2.service.safety.SafetyValidationException;

class RenExceptionHandlerTest {

    @Test
    void exposesSafetyValidationErrorCodeInResponseMessage() {
        RenExceptionHandler handler = new RenExceptionHandler();

        Result<Void> response = handler.handleSafetyValidationException(
                new SafetyValidationException(SafetyErrorCode.E_RUNTIME_STALE, "cached runtime status is stale"));

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, response.getCode());
        assertEquals("E_RUNTIME_STALE: cached runtime status is stale", response.getMsg());
    }

    @Test
    void exposesEntitlementValidationErrorCodeInResponseMessage() {
        RenExceptionHandler handler = new RenExceptionHandler();

        Result<Void> response = handler.handleEntitlementValidationException(
                new EntitlementValidationException("font", "kai_premium_v1"));

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, response.getCode());
        assertEquals("E_NOT_ENTITLED: font:kai_premium_v1", response.getMsg());
    }

    @Test
    void exposesPrimarySessionErrorCodeInResponseMessage() {
        RenExceptionHandler handler = new RenExceptionHandler();

        Result<Void> response = handler.handlePrimarySessionException(
                new PrimarySessionException("E_NOT_PRIMARY", "current session is not primary"));

        assertEquals(ErrorCode.FORBIDDEN, response.getCode());
        assertEquals("E_NOT_PRIMARY: current session is not primary", response.getMsg());
    }
}
