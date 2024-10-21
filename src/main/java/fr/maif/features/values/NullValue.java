package fr.maif.features.values;

import java.math.BigDecimal;

public class NullValue implements FeatureValue{
    @Override
    public String stringValue() {
        return null;
    }

    @Override
    public Boolean booleanValue(BooleanCastStrategy castStrategy) {
        return false;
    }

    @Override
    public BigDecimal numberValue() {
        return null;
    }
}
