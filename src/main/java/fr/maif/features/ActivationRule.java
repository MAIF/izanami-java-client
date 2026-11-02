package fr.maif.features;

import java.util.Set;

public interface ActivationRule {
    boolean active(String user, String feature, Set<String> groups);
}
