package fr.maif.requests.events;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import fr.maif.ClientConfiguration;
import fr.maif.FeatureCacheConfiguration;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.requests.FeatureRequest;
import fr.maif.requests.IzanamiConnectionInformation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class SSEClientReconnectionTest {
    private WireMockServer server;
    private RecordingSSEClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        var connection = IzanamiConnectionInformation.connectionInformation()
                .withUrl(server.baseUrl())
                .withClientId("client")
                .withClientSecret("secret");
        client = new RecordingSSEClient(new ClientConfiguration(
                connection, null, FeatureCacheConfiguration.newBuilder().build(),
                null, Duration.ofSeconds(5), BooleanCastStrategy.LAX, 1800));
    }

    @AfterEach
    void tearDown() {
        client.close().join();
        server.stop();
    }

    @Test
    void completesPermanentFailureBeforeReconnectingAndKeepsLinearDelay() throws Exception {
        server.stubFor(get(urlPathEqualTo("/v2/events"))
                .willReturn(aResponse().withStatus(404).withFixedDelay(200)));
        CompletableFuture<Void> first = client.start();

        assertThatThrownBy(() -> first.get(3, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to connect to Izanami backend");
        await().atMost(Duration.ofSeconds(3)).until(() -> client.reconnectTimes.size() >= 2);
        assertThat(client.completedBeforeRetry).isTrue();
        await().atMost(Duration.ofSeconds(13)).until(() -> client.reconnectTimes.size() >= 3);
        assertThat(Duration.ofNanos(client.reconnectTimes.get(2) - client.reconnectTimes.get(1)))
                .isGreaterThanOrEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void completesTransientFailureNormallyBeforeReconnecting() throws Exception {
        server.stubFor(get(urlPathEqualTo("/v2/events"))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE).withFixedDelay(200)));
        CompletableFuture<Void> first = client.start();

        assertThat(first.get(3, TimeUnit.SECONDS)).isNull();
        await().atMost(Duration.ofSeconds(3)).until(() -> client.reconnectTimes.size() >= 2);
        assertThat(client.completedBeforeRetry).isTrue();
    }

    @Test
    void intentionalCancellationDoesNotReconnect() {
        server.stubFor(get(urlPathEqualTo("/v2/events"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(2000)));
        client.start();
        await().atMost(Duration.ofSeconds(3))
                .until(() -> server.countRequestsMatching(getRequestedFor(urlPathEqualTo("/v2/events")).build()).getCount() > 0);

        client.disconnect().join();

        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2))
                .until(() -> client.reconnectTimes.size() == 1);
    }

    private static class RecordingSSEClient extends SSEClient {
        private final CopyOnWriteArrayList<Long> reconnectTimes = new CopyOnWriteArrayList<>();
        private volatile CompletableFuture<Void> firstFuture;
        private volatile boolean completedBeforeRetry;

        private RecordingSSEClient(ClientConfiguration configuration) {
            super(configuration);
        }

        private CompletableFuture<Void> start() {
            firstFuture = reconnectWith(FeatureRequest.newFeatureRequest(), (connectionId, eventId, event) -> {});
            return firstFuture;
        }

        @Override
        public CompletableFuture<Void> reconnect() {
            if (!reconnectTimes.isEmpty()) {
                completedBeforeRetry = firstFuture != null && firstFuture.isDone();
            }
            reconnectTimes.add(System.nanoTime());
            return super.reconnect();
        }
    }
}
