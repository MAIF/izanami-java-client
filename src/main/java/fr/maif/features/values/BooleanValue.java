package fr.maif.features.values;

import java.math.BigDecimal;

public class BooleanValue implements FeatureValue {
    private Boolean value;

    public BooleanValue(Boolean value) {
        this.value = value;
    }

    @Override
    public String stringValue() {
        throw new IllegalArgumentException("Cannot convert Boolean to string");
    }

    @Override
    public Boolean booleanValue(BooleanCastStrategy castStrategy) {
        return value;
    }

    @Override
    public BigDecimal numberValue() {
        throw new IllegalArgumentException("Cannot convert Boolean to bigdecimal");
    }
}
