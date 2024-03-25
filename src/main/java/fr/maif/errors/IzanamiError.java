package fr.maif.errors;

public class IzanamiError extends RuntimeException {
    public final String message;

    public IzanamiError(String message) {
        this.message = message;
    }

}
