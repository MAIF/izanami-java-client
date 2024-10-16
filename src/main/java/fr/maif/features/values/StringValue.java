package fr.maif.features.values;

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
    public boolean booleanValue(BooleanCastStrategy castStrategy) {
        switch (castStrategy) {
            case STRICT:
                throw new IllegalArgumentException("Cannot convert String to boolean in STRICT mode");
            case LAX:
                return value != null && !value.isEmpty();
            default:
                throw new IllegalArgumentException("Unknown BooleanCastStrategy: " + castStrategy);
        }
    }

    @Override
    public BigDecimal numberValue() {
        throw new IllegalArgumentException("Cannot convert String to BigDecimal");
    }
}
