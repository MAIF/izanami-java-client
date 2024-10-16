package fr.maif.features.values;

import java.math.BigDecimal;

public interface FeatureValue {
    String stringValue();
    Boolean booleanValue(BooleanCastStrategy castStrategy);
    BigDecimal numberValue();
}
