package fr.maif.features;

import org.apache.commons.codec.digest.MurmurHash3;

public class UserPercentage implements ActivationRule {
    public Integer percentage;

    public UserPercentage(Integer percentage) {
        this.percentage = percentage;
    }

    @Override
    public boolean active(String user, String feature) {
        String toHash = feature + "-" + user;
        var bytes = toHash.getBytes();
        long hash = (Math.abs(MurmurHash3.hash32x86(bytes, 0, bytes.length, 42)) % 100) + 1;
        return hash <= percentage;
    }
}
