package fr.maif.http;

import fr.maif.ClientConfiguration;
import fr.maif.features.Feature;
import fr.maif.requests.FeatureRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class HttpRequester {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequester.class);

    static String url(ClientConfiguration configuration, FeatureRequest request) {
        var url = configuration.connectionInformation.url  + "/v2/features";

        var maybeFeatures = request.getFeatures().stream().sorted(String::compareTo).collect(Collectors.joining(","));

        var params = new TreeMap<>();
        params.put("conditions", true);
        if(!maybeFeatures.isBlank()) {
            params.put("features", maybeFeatures);
        }
        request.getContext().ifPresent(ctx -> params.put("context", ctx));

        Optional.ofNullable(request.getUser()).filter(str -> !str.isBlank())
                .map(user -> params.put("user", user));


        String searchPart = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));

        url = !searchPart.isBlank() ? (url + "?" + searchPart) : url;

        return url;
    }

    static <T> CompletableFuture<Result<T>> performCall(
            ClientConfiguration configuration,
            IzanamiHttpRequest request,
            Function<String, Result<T>> responseMapper
    ) {
        return configuration.httpClient.apply(request)
                // TODO handle error
                .thenApply(resp -> responseMapper.apply(resp.body));
    }
    public static CompletableFuture<Result<Map<String, Feature>>> performRequest(
            ClientConfiguration configuration,
            FeatureRequest request
    ) {
        var url = url(configuration, request);
        var method = request.getPayload().map(p -> IzanamiHttpRequest.Method.POST).orElse(IzanamiHttpRequest.Method.GET);
        var r = new IzanamiHttpRequest();
        r.body = request.getPayload();
        r.method = method;
        r.headers = configuration.connectionInformation.headers();
        r.timeout = request.getTimeout().orElseGet(() -> configuration.callTimeout);
        r.uri = URI.create(url);
        LOGGER.debug("Calling {}", url);
        return performCall(configuration, r, ResponseUtils::parseFeatureResponse);
    }
}
