package fr.maif.features.values;

import fr.maif.errors.IzanamiException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StringValueTest {

    @Test
    void testStringValue() {
        StringValue value = new StringValue("test");
        assertEquals("test", value.stringValue());
        assertEquals("test", value.stringValue());
    }

    @Test
    void testBooleanValueStrict() {
        StringValue value = new StringValue("test");
        assertThrows(IzanamiException.class, () -> value.booleanValue(BooleanCastStrategy.STRICT));
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
        assertThrows(IllegalArgumentException.class, () -> value.numberValue());
        assertThrows(IllegalArgumentException.class, () -> value.numberValue());
    }
}
