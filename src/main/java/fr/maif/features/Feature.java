package fr.maif.features;

import java.util.*;

public class Feature {
            public final String id;
            public final String name;
            public final String project;
            public final boolean active;

            public Map<String, FeatureOverload> conditions = new HashMap<>();

    public Feature(String id, String name, String project, boolean active, Map<String, FeatureOverload> conditions) {
        this.id = id;
        this.name = name;
        this.project = project;
        this.active = active;
        this.conditions = conditions;
    }

    public Optional<Boolean> active(String context, String user) {
        String ctx = Optional.ofNullable(context).orElse("");
        String contextToUse = conditions.keySet().stream()
                .filter(ctx::startsWith)
                .max(Comparator.comparingInt(String::length))
                .get();

        FeatureOverload overload = conditions.get(contextToUse);
        return overload.active(user, name);
    }

        public static enum FeatureType {
            CLASSICAL,
            SCRIPT
        }
}
