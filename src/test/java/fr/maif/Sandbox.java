package fr.maif;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.maif.features.Feature;
import fr.maif.http.ResponseUtils;
import fr.maif.requests.FeatureRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static fr.maif.requests.IzanamiConnectionInformation.connectionInformation;
import static java.util.concurrent.TimeUnit.SECONDS;

public class Sandbox {
    @Test
    public void foo() throws URISyntaxException, IOException, InterruptedException {
        String clientId = "test_H7mD7i0gV8MtJgXY";
        String clientSecret = "aVUm9dXMp26D3TIlebmwAe5we44kDR0tm83mfwL5zBo09Wy15Bg122IizWZQPOBm";
        String f1 = "d64dccb4-06e9-4b99-b857-684f44cdd584";
        String f2 = "08bd325a-7132-460a-a397-2ca0c7d09a3d";
        String f3 = "legyacy";

        var client = IzanamiClient.newBuilder(
                connectionInformation()
                        .withUrl("http://localhost:9000/api")
                        .withClientId(clientId)
                        .withClientSecret(clientSecret)
        ).withCacheConfiguration(
                FeatureCacheConfiguration.newBuilder()
                .shouldUseServerSentEvent(true)
                .withServerSentEventKeepAliveInterval(Duration.ofSeconds(3L))
                .build()
        ).withPreloadedFeatures(f3)
                .withCallTimeout(Duration.ofSeconds(10L))
        .build();

        client.isLoaded().join();
        System.out.println("Client is loaded");

        var result = client.checkFeatureActivations(FeatureRequest.newFeatureRequest().withFeatures(f3)).join();

        var strResult = ResponseUtils.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        System.out.println(strResult);

        while(true) {
            Thread.sleep(1000);
        }
    }


}
