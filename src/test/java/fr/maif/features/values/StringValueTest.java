package fr.maif.features.values;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StringValueTest {

    @Test
    void testStringValue() {
        StringValue value = new StringValue("test");
        assertEquals("test", value.stringValue(BooleanCastStrategy.STRICT));
        assertEquals("test", value.stringValue(BooleanCastStrategy.LAX));
    }

    @Test
    void testBooleanValueStrict() {
        StringValue value = new StringValue("test");
        assertThrows(IllegalArgumentException.class, () -> value.booleanValue(BooleanCastStrategy.STRICT));
    }

    @Test
    void testBooleanValueLax() {
        StringValue nonEmptyValue = new StringValue("test");
        StringValue emptyValue = new StringValue("");
        StringValue nullValue = new StringValue(null);

        assertTrue(nonEmptyValue.booleanValue(BooleanCastStrategy.LAX));
        assertFalse(emptyValue.booleanValue(BooleanCastStrategy.LAX));
        assertFalse(nullValue.booleanValue(BooleanCastStrategy.LAX));
    }

    @Test
    void testNumberValueThrowsException() {
        StringValue value = new StringValue("test");
        assertThrows(IllegalArgumentException.class, () -> value.numberValue(BooleanCastStrategy.STRICT));
        assertThrows(IllegalArgumentException.class, () -> value.numberValue(BooleanCastStrategy.LAX));
    }
}
