package fr.maif.features;

import org.apache.commons.codec.digest.MurmurHash3;

import java.util.Set;

public class UserPercentage implements ActivationRule {
    public Integer percentage;

    public UserPercentage(Integer percentage) {
        this.percentage = percentage;
    }

    @Override
    public boolean active(String user, String feature, Set<String> groups) {
        String toHash = feature + "-" + user;
        var bytes = toHash.getBytes();
        long hash = (Math.abs(MurmurHash3.hash32x86(bytes, 0, bytes.length, 42)) % 100) + 1;
        return hash <= percentage;
    }
}
