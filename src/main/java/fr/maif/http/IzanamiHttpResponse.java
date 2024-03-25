package fr.maif.http;

public class IzanamiHttpResponse {
    public String body;
    public int status;

    public IzanamiHttpResponse(String body, int status) {
        this.body = body;
        this.status = status;
    }
}