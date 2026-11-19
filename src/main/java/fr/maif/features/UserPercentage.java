package fr.maif.features;

import org.apache.commons.codec.digest.MurmurHash3;

import java.util.Set;

public class UserPercentage implements ActivationRule {
    public Integer percentage;

    public UserPercentage(Integer percentage) {
        this.percentage = percentage;
    }
}
