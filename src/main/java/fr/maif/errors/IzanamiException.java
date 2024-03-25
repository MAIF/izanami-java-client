package fr.maif.errors;

public class IzanamiException extends RuntimeException {
    public IzanamiException(String message) {
        super(message);
    }

    public IzanamiException(Exception e) {
        super(e);
    }
}
