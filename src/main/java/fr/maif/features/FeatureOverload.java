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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;

public abstract class FeatureOverload<T extends FeatureValue> {
    public final boolean enabled;
    public final Feature.FeatureType featureType;

    public abstract Optional<T> value(String user, String name, Set<String> groups);

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
        public Optional<BooleanValue> value(String user, String name, Set<String> groups) {
            if(!enabled) {
                return Optional.of(new BooleanValue(false));
            }
            if(conditions.isEmpty()) {
                return Optional.of( new BooleanValue(true));
            }
            return Optional.of( new BooleanValue(conditions.stream().anyMatch(cond -> {
                boolean isPeriodActive = Optional.ofNullable(cond.period).map(EvaluationService::isPeriodActive).orElse(true);
                boolean isRuleActive = Optional.ofNullable(cond.rule).map(r -> {
                    if(r instanceof UserOrGroupList) {
                        return EvaluationService.isUserOrGroupRuleActive((UserOrGroupList) r, user, groups);
                    } else if(r instanceof UserPercentage) {
                        var percentageRule = (UserPercentage) r;
                        return EvaluationService.isPercentageRuleActive(percentageRule, name, user, 0);
                    } else {
                        throw new IllegalArgumentException("Unknown rule type " + r.getClass().getName());
                    }
                }).orElse(true);

                return isPeriodActive && isRuleActive;
            })));
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
        public Optional<T> value(String user, String name, Set<String> groups) {
            if (!enabled) {
                return null;
            }
            AtomicInteger rangeStart = new AtomicInteger(0);
            T value = this.value;
            for(ValuedActivationCondition<T> condition: conditions) {
                boolean periodActive = Optional.ofNullable(condition.period).map(EvaluationService::isPeriodActive).orElse(true);
                boolean ruleActive = Optional.ofNullable(condition.rule).map(r -> {
                    if(r instanceof UserOrGroupList) {
                        return EvaluationService.isUserOrGroupRuleActive((UserOrGroupList) r, user, groups);
                    } else if(r instanceof UserPercentage) {
                        var percentageRule = (UserPercentage) r;
                        return EvaluationService.isPercentageRuleActive(percentageRule, name, user, rangeStart.getAndAdd(percentageRule.percentage));
                    } else {
                        throw new IllegalArgumentException("Unknown rule type " + r.getClass().getName());
                    }
                }).orElse(true);
                if(periodActive && ruleActive) {
                    value = condition.value;
                    break;
                }
            }

            if (value == null) {
                return null;
            }

            return Optional.of(value);
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
        public Optional<T> value(String user, String name, Set<String> groups) {
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
