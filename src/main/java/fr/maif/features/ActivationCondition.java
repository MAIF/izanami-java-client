package fr.maif.features;

import java.math.BigDecimal;
import java.util.Optional;

import fr.maif.features.values.FeatureValue;
import fr.maif.features.values.StringValue;
import fr.maif.features.values.NumberValue;

public class ActivationCondition {
    private FeaturePeriod period;
    private ActivationRule rule;

    public ActivationCondition(FeaturePeriod period, ActivationRule rule) {
        this.period = period;
        this.rule = rule;
    }

    public boolean active(String user, String featureId) {
        return Optional.ofNullable(period).map(p -> p.active(user)).orElse(true) &&
                Optional.ofNullable(rule).map(r -> r.active(user, featureId)).orElse(true);
    }

    public static abstract class ValuedActivationCondition<T extends FeatureValue> extends ActivationCondition {
        public T value;

        public ValuedActivationCondition(FeaturePeriod period, ActivationRule rule, T value) {
            super(period, rule);
            this.value = value;
        }
    }

    public static class StringValuedActivationCondition extends ValuedActivationCondition<StringValue> {
        public StringValuedActivationCondition(FeaturePeriod period, ActivationRule rule, String value) {
            super(period, rule, new StringValue(value));
        }

        public static StringValuedActivationCondition fromCondition(ActivationCondition cond, String value) {
            return new StringValuedActivationCondition(cond.period, cond.rule, value);
        }
    }

    public static class NumberValuedActivationCondition extends ValuedActivationCondition<NumberValue> {
        public NumberValuedActivationCondition(FeaturePeriod period, ActivationRule rule, BigDecimal value) {
            super(period, rule, new NumberValue(value));
        }

        public static NumberValuedActivationCondition fromCondition(ActivationCondition cond, BigDecimal value) {
            return new NumberValuedActivationCondition(cond.period, cond.rule, value);
        }
    }
}
