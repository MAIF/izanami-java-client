package fr.maif.features;

import java.util.Optional;
import java.util.Set;

public abstract class FeatureOverload {
    public final boolean enabled;
    public final Feature.FeatureType featureType;
    public Optional<Boolean> active(String user, String name) {
        if(featureType == Feature.FeatureType.CLASSICAL) {
            ClassicalOverload o = (ClassicalOverload)this;
            boolean active = enabled && (
                    o.conditions.isEmpty() ||
                            o.conditions.stream().anyMatch(cond -> cond.active(user, name))
            );
            return Optional.of(active);
        } else {
            return Optional.empty();
        }
    }

    public FeatureOverload(Feature.FeatureType featureType, boolean enabled) {
        this.featureType = featureType;
        this.enabled = enabled;
    }

    public static class ClassicalOverload extends FeatureOverload {
        public Set<ActivationCondition> conditions;

        public ClassicalOverload(boolean enabled, Set<ActivationCondition> conditions) {
            super(Feature.FeatureType.CLASSICAL, enabled);
            this.conditions = conditions;
        }
    }

    public static class WasmFeatureOverload extends FeatureOverload {
        public WasmConfig wasmConfig;

        public WasmFeatureOverload(boolean enabled, WasmConfig wasmConfig) {
            super(Feature.FeatureType.SCRIPT, enabled);
            this.wasmConfig = wasmConfig;
        }
    }

    public static class WasmConfig {
        public final String name;

        public WasmConfig(String name) {
            this.name = name;
        }
    }
}


