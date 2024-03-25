package fr.maif.features;

import java.util.Optional;

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
}
