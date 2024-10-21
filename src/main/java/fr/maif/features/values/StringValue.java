package fr.maif.features.values;

import fr.maif.errors.IzanamiException;

import java.math.BigDecimal;

public class StringValue implements FeatureValue {
    private String value;

    public StringValue(String value) {
        this.value = value;
    }

    @Override
    public String stringValue() {
        return value;
    }

    @Override
    public Boolean booleanValue(BooleanCastStrategy castStrategy) {
        switch (castStrategy) {
            case STRICT:
                throw new IzanamiException("Cannot convert String to boolean in STRICT mode");
            case LAX:
                return value != null && !value.isEmpty();
            default:
                throw new IzanamiException("Unknown BooleanCastStrategy: " + castStrategy);
        }
    }

    @Override
    public BigDecimal numberValue() {
        throw new IllegalArgumentException("Cannot convert String to BigDecimal");
    }
}
