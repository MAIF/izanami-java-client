package fr.maif.features.values;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class NumberValueTest {

    @Test
    void testNumberValue() {
        NumberValue value = new NumberValue(new BigDecimal("42.5"));
        assertEquals(new BigDecimal("42.5"), value.numberValue());
        assertEquals(new BigDecimal("42.5"), value.numberValue());
    }

    @Test
    void testBooleanValueStrict() {
        NumberValue nonZeroValue = new NumberValue(new BigDecimal("42.5"));
        NumberValue zeroValue = new NumberValue(BigDecimal.ZERO);

        assertThrows(IllegalArgumentException.class, () -> nonZeroValue.booleanValue(BooleanCastStrategy.STRICT));
        assertThrows(IllegalArgumentException.class, () -> zeroValue.booleanValue(BooleanCastStrategy.STRICT));
    }

    @Test
    void testBooleanValueLax() {
        NumberValue nonZeroValue = new NumberValue(new BigDecimal("42.5"));
        NumberValue zeroValue = new NumberValue(BigDecimal.ZERO);

        assertTrue(nonZeroValue.booleanValue(BooleanCastStrategy.LAX));
        assertFalse(zeroValue.booleanValue(BooleanCastStrategy.LAX));
    }

    @Test
    void testStringValueThrowsException() {
        NumberValue value = new NumberValue(new BigDecimal("42.5"));
        assertThrows(IllegalArgumentException.class, value::stringValue);
        assertThrows(IllegalArgumentException.class, value::stringValue);
    }
}
