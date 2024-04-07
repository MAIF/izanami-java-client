package fr.maif.requests.events;

import fr.maif.features.Feature;

import java.util.Map;

public interface IzanamiEvent {
    public static class FeatureStates implements IzanamiEvent {
        public Map<String, Feature> features;

        public FeatureStates(Map<String, Feature> features) {
            this.features = features;
        }
    }

    public static class FeatureCreated implements IzanamiEvent {
        public Feature feature;

        public FeatureCreated(Feature feature) {
            this.feature = feature;
        }
    }

    public static class FeatureUpdated implements IzanamiEvent {
        public Feature feature;

        public FeatureUpdated(Feature feature) {
            this.feature = feature;
        }
    }

    public static class FeatureDeleted implements IzanamiEvent {
        public String feature;

        public FeatureDeleted(String feature) {
            this.feature = feature;
        }
    }
}
