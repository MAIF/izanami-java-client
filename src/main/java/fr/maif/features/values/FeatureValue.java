package fr.maif.features.values;

import java.math.BigDecimal;

public interface FeatureValue {
    String stringValue();
    boolean booleanValue(BooleanCastStrategy castStrategy);
    BigDecimal numberValue();
}
