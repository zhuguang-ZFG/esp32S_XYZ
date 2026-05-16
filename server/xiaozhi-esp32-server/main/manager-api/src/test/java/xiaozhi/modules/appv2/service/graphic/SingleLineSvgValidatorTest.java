package xiaozhi.modules.appv2.service.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SingleLineSvgValidatorTest {
    private final SingleLineSvgValidator validator = new SingleLineSvgValidator();

    @Test
    void acceptsSingleBlackNoFillPath() {
        SvgValidationResult result = validator.validate(
                "<svg viewBox=\"0 0 10 10\"><path d=\"M0 0 L10 10\" fill=\"none\" stroke=\"black\"/></svg>");

        assertTrue(result.isValid());
        assertEquals(1, result.getPathCount());
        assertEquals(2, result.getCommandCount());
    }

    @Test
    void rejectsFilledShape() {
        DrawingValidationException error = assertThrows(
                DrawingValidationException.class,
                () -> validator.requireValid("<svg><rect x=\"0\" y=\"0\" width=\"10\" height=\"10\" fill=\"red\"/></svg>"));

        assertEquals("E_INVALID_DRAWING", error.getErrorCode());
        assertEquals("filled_shape", error.getReason());
    }

    @Test
    void rejectsMultiplePaths() {
        SvgValidationResult result = validator.validate(
                "<svg><path d=\"M0 0 L1 1\" fill=\"none\" stroke=\"black\"/><path d=\"M1 1 L2 2\" fill=\"none\" stroke=\"black\"/></svg>");

        assertEquals(false, result.isValid());
        assertEquals("path_count", result.getReason());
    }

    @Test
    void rejectsNonBlackStroke() {
        SvgValidationResult result = validator.validate(
                "<svg><path d=\"M0 0 L1 1\" fill=\"none\" stroke=\"red\"/></svg>");

        assertEquals(false, result.isValid());
        assertEquals("non_black_stroke", result.getReason());
    }
}
