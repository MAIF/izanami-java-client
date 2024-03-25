package fr.maif.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface IzanamiHttpClient extends Function<IzanamiHttpRequest, CompletableFuture<IzanamiHttpResponse>> {
    class DefaultIzanamiHttpClient implements IzanamiHttpClient {
        private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIzanamiHttpClient.class);
        public final HttpClient client;

        public DefaultIzanamiHttpClient() {
            this.client = HttpClient.newHttpClient();
        }

        @Override
        public CompletableFuture<IzanamiHttpResponse> apply(IzanamiHttpRequest request) {
            var requestBuilder = HttpRequest.newBuilder().timeout(request.timeout);
            request.headers.forEach(requestBuilder::setHeader);

            var r = requestBuilder.uri(request.uri);
            if(request.method == IzanamiHttpRequest.Method.GET) {
                r.GET();
            } else {
                r.POST(HttpRequest.BodyPublishers.ofString(request.body.orElse("")));
            }

            return client.sendAsync(r.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        LOGGER.debug("Response for {} : {} (status is {})", request.uri, response.body(), response.statusCode());
                        return new IzanamiHttpResponse(response.body(), response.statusCode());
                    }).whenComplete((resp, ex) -> {
                        if(Objects.nonNull(ex)) {
                            LOGGER.error("Failed to perform http request", ex);
                        }
                    });
        }
    }

}

