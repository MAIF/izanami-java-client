package fr.maif.features;

import java.util.Optional;
import java.util.List;

import fr.maif.features.ActivationCondition.NumberValuedActivationCondition;
import fr.maif.features.ActivationCondition.StringValuedActivationCondition;
import fr.maif.features.ActivationCondition.ValuedActivationCondition;
import fr.maif.features.values.BooleanValue;
import fr.maif.features.values.FeatureValue;
import fr.maif.features.values.NumberValue;
import fr.maif.features.values.StringValue;
import java.math.BigDecimal;
public abstract class FeatureOverload<T extends FeatureValue> {
    public final boolean enabled;
    public final Feature.FeatureType featureType;

    public abstract Optional<T> value(String user, String name);

    public FeatureOverload(Feature.FeatureType featureType, boolean enabled) {
        this.featureType = featureType;
        this.enabled = enabled;
    }

    public static class ClassicalOverload extends FeatureOverload<BooleanValue> {
        public List<ActivationCondition> conditions;

        public ClassicalOverload(boolean enabled, List<ActivationCondition> conditions) {
            super(Feature.FeatureType.CLASSICAL, enabled);
            this.conditions = conditions;
        }

        @Override
        public Optional<BooleanValue> value(String user, String name) {
            if(!enabled) {
                return Optional.of(new BooleanValue(false));
            }
            if(conditions.isEmpty()) {
                return Optional.of( new BooleanValue(true));
            }
            return Optional.of( new BooleanValue(conditions.stream().anyMatch(cond -> cond.active(user, name))));
        }
    }

    public static abstract class ValuedOverload<T extends FeatureValue> extends FeatureOverload<T> {
        public List<? extends ValuedActivationCondition<T>> conditions;
        public T value;
        public ValuedOverload(boolean enabled, List<? extends ValuedActivationCondition<T>> conditions, T value) {
            super(Feature.FeatureType.CLASSICAL, enabled);
            this.conditions = conditions;
            this.value = value;
        }   
        
        
        @Override
        public Optional<T> value(String user, String name) {
            if (!enabled) {
                return null;
            }
            T result = conditions.stream().filter(cond -> cond.active(user, name))
                    .map(cond -> cond.value)
                    .findFirst()
                    .orElse(value);

            if (result == null) {
                return null;
            }

            return Optional.of(result);
        }

      
    }

    public static class StringOverload extends ValuedOverload<StringValue> {
        public StringOverload(boolean enabled, List<StringValuedActivationCondition> conditions, StringValue value) {
            super(enabled, conditions, value);
    
        }

        public StringOverload(boolean enabled, List<StringValuedActivationCondition> conditions, String value) {
            super(enabled, conditions, new StringValue(value));
    
        }
    }

    public static class NumberOverload extends ValuedOverload<NumberValue> {
        public NumberOverload(boolean enabled, List<NumberValuedActivationCondition> conditions, NumberValue value) {
            super(enabled, conditions, value);
    
        }

        public NumberOverload(boolean enabled, List<NumberValuedActivationCondition> conditions, BigDecimal value) {
            super(enabled, conditions, new NumberValue(value));
    
        }
    }

    public static class WasmFeatureOverload<T extends FeatureValue> extends FeatureOverload<T> {
        public WasmConfig wasmConfig;

        public WasmFeatureOverload(boolean enabled, WasmConfig wasmConfig) {
            super(Feature.FeatureType.SCRIPT, enabled);
            this.wasmConfig = wasmConfig;
        }

        @Override
        public Optional<T> value(String user, String name) {
            return Optional.empty();
        }
    }

    public static class WasmConfig {
        public final String name;

        public WasmConfig(String name) {
            this.name = name;
        }
    }
}
