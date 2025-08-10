package fr.maif;

import fr.maif.http.ResponseUtils;
import fr.maif.requests.FeatureRequest;
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
    //@Test
    public void foo() throws URISyntaxException, IOException, InterruptedException {
        String clientId = "tenant_ArfEn0nS2O8uK6La";
        String clientSecret = "v34NojGKFd2IuPSPhTHyM00VZiJD6yNBJKqZvAiVz3Z4TAzeNmbHOjJnqEspeF0x";
        String f1 = "e4b0d23e-89da-4ebf-a906-8388ab625816";

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
        )
        .build();

        client.isLoaded().join();
        System.out.println("Client is loaded");

        var result = client.booleanValue(FeatureRequest.newSingleFeatureRequest(f1)).join();
        var result2 = client.booleanValue(FeatureRequest.newSingleFeatureRequest(f1).withContext("dev/bar")).join();
        var result3 = client.booleanValue(FeatureRequest.newSingleFeatureRequest(f1)).join();

        System.out.println(result3);
    }


}
