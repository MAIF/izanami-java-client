package fr.maif.features;

public interface ActivationRule {
    boolean active(String user, String feature);
}
