package fr.maif.features;

import java.util.*;
import java.math.BigDecimal;

import fr.maif.features.values.FeatureValue;
import fr.maif.features.values.NumberValue;
import fr.maif.features.values.StringValue;
import fr.maif.features.values.BooleanValue;

public abstract class Feature<T extends FeatureValue> {
    public final String id;
    public final String name;
    public final String project;
    public final T active;

    public Map<String, FeatureOverload<T>> conditions = new HashMap<>();

    public Feature(String id, String name, String project, T active,
                Map<String, FeatureOverload<T>> conditions) {
            this.id = id;
            this.name = name;
            this.project = project;
            this.active = active;
            this.conditions = conditions;
        }


    public Optional<T> value(String context, String user) {
        String ctx = Optional.ofNullable(context).orElse("");
        String contextToUse = conditions.keySet().stream()
                .filter(ctx::startsWith)
                .max(Comparator.comparingInt(String::length))
                .get();

        FeatureOverload<T> overload = conditions.get(contextToUse);
        return overload.value(user, name);
    }


    public static class BooleanFeature extends Feature<BooleanValue> {
        public BooleanFeature(String id, String name, String project, Boolean active,
                Map<String, FeatureOverload<BooleanValue>> conditions) {
            super(id, name, project, new BooleanValue(active), conditions);
        }
    }

    public static class StringFeature extends Feature<StringValue> {
        public StringFeature(String id, String name, String project, String active,
                Map<String, FeatureOverload<StringValue>> conditions) {
            super(id, name, project, new StringValue(active), conditions);
        }
    }


    public static class NumberFeature extends Feature<NumberValue> {
        public NumberFeature(String id, String name, String project, BigDecimal active,
                Map<String, FeatureOverload<NumberValue>> conditions) {
            super(id, name, project, new NumberValue(active), conditions);
        }
    }

    public static enum FeatureType {
        CLASSICAL,
        SCRIPT
    }
}
