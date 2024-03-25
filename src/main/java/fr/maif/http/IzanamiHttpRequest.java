package fr.maif.http;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class IzanamiHttpRequest {
    public enum Method {
        GET, POST
    }

    public URI uri;
    public Optional<String> body = Optional.empty();
    public Method method = Method.GET;
    public Duration timeout;
    public Map<String, String> headers = new HashMap<>();
}
