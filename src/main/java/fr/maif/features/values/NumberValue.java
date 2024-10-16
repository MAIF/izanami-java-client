package fr.maif.features.values;

import java.math.BigDecimal;

public class NumberValue implements FeatureValue {
    private BigDecimal value;

    public NumberValue(BigDecimal value) {
        this.value = value;
    }

    @Override
    public String stringValue() {
        throw new IllegalArgumentException("Cannot convert BigDecimal to String");
    }

    @Override
    public Boolean booleanValue(BooleanCastStrategy castStrategy) {
        switch (castStrategy) {
            case STRICT:
                throw new IllegalArgumentException("Cannot convert BigDecimal to boolean in STRICT mode");
            case LAX:
                return value != null && value.compareTo(BigDecimal.ZERO) != 0;
            default:
                throw new IllegalArgumentException("Unknown BooleanCastStrategy: " + castStrategy);
        }
    }

    @Override
    public BigDecimal numberValue() {
        return value;
    }
}
