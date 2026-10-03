package fr.maif.http;

import fr.maif.ClientConfiguration;
import fr.maif.features.Feature;
import fr.maif.requests.FeatureRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class HttpRequester {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequester.class);


    public static TreeMap<String, String> queryParametersAsMap(FeatureRequest request, boolean shouldUseBodyForFeatures) {
        var maybeFeatures = request.getFeatures().stream().sorted(String::compareTo).collect(Collectors.joining(","));

        var params = new TreeMap<String, String>();
        params.put("conditions", "true");
        if(!maybeFeatures.isBlank() && !shouldUseBodyForFeatures) {
            params.put("features", maybeFeatures);
        }
        request.getContext().ifPresent(ctx -> params.put("context", ctx));

        Optional.ofNullable(request.getUser()).filter(str -> !str.isBlank())
                .map(user -> params.put("user", user));


        return params;
    }
    public static String queryParameters(FeatureRequest request, boolean shouldUseBodyForFeatures) {
        return queryParametersAsMap(request, shouldUseBodyForFeatures).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    static String url(ClientConfiguration configuration, FeatureRequest request, boolean shouldUseBodyForFeatures) {
        var url = configuration.connectionInformation.url  + "/v2/" + (shouldUseBodyForFeatures ? "_features" : "features");

        String searchPart = queryParameters(request, shouldUseBodyForFeatures);

        url = !searchPart.isBlank() ? (url + "?" + searchPart) : url;

        return url;
    }

    public static boolean isURLTooLong(ClientConfiguration configuration, FeatureRequest request) {
        var url = configuration.connectionInformation.url  + "/v2/features";
        return url.length() > configuration.maxUrlSize;
    }

    static <T> CompletableFuture<Result<T>> performCall(
            ClientConfiguration configuration,
            IzanamiHttpRequest request,
            Function<String, Result<T>> responseMapper
    ) {
        return configuration.httpClient.apply(request)
                .thenApply(resp -> responseMapper.apply(resp.body));
    }
    public static CompletableFuture<Result<Map<String, Feature>>> performRequest(
            ClientConfiguration configuration,
            FeatureRequest request
    ) {
        Map<String, String> headers = new HashMap<>(configuration.connectionInformation.headers());
        String url = url(configuration, request, false);
        boolean urlTooLongForGet = false;
        if(url.length() > configuration.maxUrlSize) {
           urlTooLongForGet = true;
           url = url(configuration, request, true);
        }
        IzanamiHttpRequest.Method method = IzanamiHttpRequest.Method.GET;
        Optional<String> body = request.getPayload();
        if(request.getPayload().isPresent() || urlTooLongForGet) {
            headers.put("content-type", "application/json");
            method = IzanamiHttpRequest.Method.POST;
            if(urlTooLongForGet) {
                LOGGER.debug("a too long URL request was detected, client will put feature ids in body");
                String requestPart = "{\"features\": [\"" + String.join("\",\"", request.getFeatures())  + "\"] }";
                body = Optional.of("{\"request\": " + requestPart + request.getPayload().map(payload -> ", \"payload\": " + payload).orElse("") + "}");
            }
        }
        var r = new IzanamiHttpRequest();
        r.body = body;
        r.method = method;
        r.headers = headers;
        r.timeout = request.getTimeout().orElseGet(() -> configuration.callTimeout);
        r.uri = URI.create(url);
        LOGGER.debug("Calling {}", url);
        if(LOGGER.isDebugEnabled() && method == IzanamiHttpRequest.Method.POST) {
            LOGGER.debug("with body {}", body.orElse(""));
        }
        return performCall(configuration, r, ResponseUtils::parseFeatureResponse);
    }
}
