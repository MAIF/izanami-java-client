package fr.maif.features.values;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BooleanValueTest {

    @Test
    void testBooleanValue() {
        BooleanValue trueValue = new BooleanValue(true);
        BooleanValue falseValue = new BooleanValue(false);

        assertTrue(trueValue.booleanValue(BooleanCastStrategy.STRICT));
        assertFalse(falseValue.booleanValue(BooleanCastStrategy.STRICT));

        assertTrue(trueValue.booleanValue(BooleanCastStrategy.LAX));
        assertFalse(falseValue.booleanValue(BooleanCastStrategy.LAX));
    }

    @Test
    void testStringValueThrowsException() {
        BooleanValue value = new BooleanValue(true);
        assertThrows(IllegalArgumentException.class, () -> value.stringValue(BooleanCastStrategy.STRICT));
        assertThrows(IllegalArgumentException.class, () -> value.stringValue(BooleanCastStrategy.LAX));
    }

    @Test
    void testNumberValueThrowsException() {
        BooleanValue value = new BooleanValue(true);
        assertThrows(IllegalArgumentException.class, () -> value.numberValue(BooleanCastStrategy.STRICT));
        assertThrows(IllegalArgumentException.class, () -> value.numberValue(BooleanCastStrategy.LAX));
    }
}
