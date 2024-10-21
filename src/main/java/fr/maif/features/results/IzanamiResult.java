package fr.maif.features.results;

import fr.maif.FeatureClientErrorStrategy;
import fr.maif.errors.IzanamiError;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.features.values.FeatureValue;
import java.math.BigDecimal;
import java.util.Map;

public class IzanamiResult {
    public final Map<String, Result> results;
    private final BooleanCastStrategy castStrategy;
    private final FeatureClientErrorStrategy<?> defaultStrategy;

    public IzanamiResult(Map<String, Result> results, BooleanCastStrategy castStrategy, FeatureClientErrorStrategy<?> defaultStrategy) {
        this.results = results;
        this.castStrategy = castStrategy;
        this.defaultStrategy = defaultStrategy;
    }

    public String stringValue(String feature) {
        return results.getOrDefault(feature, new Error(defaultStrategy, new IzanamiError("This feature hasn't been requested"))).stringValue();
    }

    public Boolean booleanValue(String feature) {
        return results.getOrDefault(feature, new Error(defaultStrategy, new IzanamiError("This feature hasn't been requested"))).booleanValue(castStrategy);
    }

    public BigDecimal numberValue(String feature) {
        return results.getOrDefault(feature, new Error(defaultStrategy, new IzanamiError("This feature hasn't been requested"))).numberValue();
    }


    public static interface Result {
        String stringValue();
        Boolean booleanValue(BooleanCastStrategy strategy);
        BigDecimal numberValue();
    }

    public static class Success implements Result {
        private final FeatureValue value;

        public Success(FeatureValue value) {
            this.value = value;
        }

        @Override
        public String stringValue() {
            return value.stringValue();
        }

        @Override
        public Boolean booleanValue(BooleanCastStrategy strategy) {
            return value.booleanValue(strategy);
        }

        @Override
        public BigDecimal numberValue() {
            return value.numberValue();
        }
    }

    public static class Error implements Result {
        private final FeatureClientErrorStrategy<?> strategy;
        private final IzanamiError error;

        public Error(FeatureClientErrorStrategy<?> strategy, IzanamiError error) {
            this.strategy = strategy;
            this.error = error;
        }

        @Override
        public String stringValue() {
            return strategy.handleErrorForString(error).join();
        }

        @Override
        public Boolean booleanValue(BooleanCastStrategy castStrategy) {
            return strategy.handleError(error).join();
        }

        @Override
        public BigDecimal numberValue() {
            return strategy.handleErrorForNumber(error).join();
        }
    }
}
