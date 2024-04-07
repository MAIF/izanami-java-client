package fr.maif.requests.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.maif.ClientConfiguration;
import fr.maif.errors.IzanamiException;
import fr.maif.http.HttpRequester;
import fr.maif.http.ResponseUtils;
import fr.maif.requests.FeatureRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.SECONDS;

public class SSEClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(SSEClient.class);
    private final ClientConfiguration clientConfiguration;
    private final HttpClient httpClient;
    private Stream<String> currentConnection;
    private final AtomicLong connectionId = new AtomicLong(0L);

    private CompletableFuture<Void> queryFuture;
    private FeatureRequest request;
    private BiConsumer<Long, IzanamiEvent> consumer;

    private final ScheduledExecutorService lifeProbeExecutorService;

    private final ExecutorService executorService;

    private final AtomicBoolean connected = new AtomicBoolean(false);

    private final AtomicReference<LocalDateTime> lastEventDate = new AtomicReference<>(LocalDateTime.now());

    public SSEClient(ClientConfiguration clientConfiguration) {
        this.clientConfiguration = clientConfiguration;
        lifeProbeExecutorService = Executors.newSingleThreadScheduledExecutor();
        Duration keepAliveInterval = clientConfiguration.cacheConfiguration.serverSentEventKeepAliveInterval;
        Duration maxToleratedDurationWithoutEvents = keepAliveInterval.multipliedBy(3L);
        lifeProbeExecutorService.scheduleAtFixedRate(() -> {
                    if (connected.get()) {
                        Duration periodSinceLastEvent = Duration.between(lastEventDate.get(), LocalDateTime.now());
                        LOGGER.debug("Periodic event presence check, it's been {} seconds since last event (max tolerance is {})", periodSinceLastEvent.toSeconds(), maxToleratedDurationWithoutEvents.toSeconds());
                        if (maxToleratedDurationWithoutEvents.compareTo(periodSinceLastEvent) < 0) {
                            LOGGER.error("No event received since {} seconds, will try to disconnect / reconnect", periodSinceLastEvent.toSeconds());
                            reconnect();
                        }
                    }
                },
                maxToleratedDurationWithoutEvents.getSeconds(),
                maxToleratedDurationWithoutEvents.getSeconds(),
                SECONDS
        );
        this.executorService = Executors.newFixedThreadPool(2);
        this.httpClient = HttpClient.newBuilder().executor(this.executorService).build();
    }


    public CompletableFuture<Void> doConnect(FeatureRequest request, BiConsumer<Long, IzanamiEvent> consumer, long id) throws IzanamiException {
        this.request = request;
        this.consumer = consumer;
        LOGGER.debug("Connecting to remote Izanami SSE endpoint");
        Map<String, String> searchPartAsMap = HttpRequester.queryParametersAsMap(request);
        searchPartAsMap.put("refreshInterval", Long.toString(clientConfiguration.cacheConfiguration.refreshInterval.toSeconds()));
        searchPartAsMap.put("keepAliveInterval", Long.toString(clientConfiguration.cacheConfiguration.serverSentEventKeepAliveInterval.toSeconds()));

        String searchPart = searchPartAsMap.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));

        String url = clientConfiguration.connectionInformation.url + "/v2/events";
        if (!searchPart.isBlank()) {
            url = url + "?" + searchPart;
        }

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(new URI(url))
                    .setHeader("Izanami-Client-Id", clientConfiguration.connectionInformation.clientId)
                    .setHeader("Izanami-Client-Secret", clientConfiguration.connectionInformation.clientSecret)
                    .timeout(request.getTimeout().orElse(clientConfiguration.callTimeout));


            var r = request.getPayload()
                    .map(payload -> requestBuilder.POST(HttpRequest.BodyPublishers.ofString(payload)))
                    .orElseGet(requestBuilder::GET).build();

            LOGGER.debug("Calling {} with a timeout of {} seconds", r.uri().toString(), r.timeout().get().toSeconds());

            this.queryFuture = httpClient.sendAsync(r, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(resp -> {

                        if(resp.statusCode() >= 400) {
                            LOGGER.error("Izanami responded with status code {}", resp.statusCode());
                            throw new RuntimeException("Failed to connect to Izanami backend");
                        } else {
                            LOGGER.info("Connected to remote Izanami SSE endpoint");
                            connected.set(true);
                        }

                        var sseMachine = new SSEStateMachine();

                        this.currentConnection = resp.body();

                        this.currentConnection.map(line -> {
                                    var res = sseMachine.addLine(line);
                                    return res.flatMap(EventService::fromSSE);
                                })
                                .flatMap(Optional::stream)
                                .forEach(evt -> {
                                    lastEventDate.set(LocalDateTime.now());
                                    consumer.accept(id, evt);
                                });
                    }).exceptionally(e -> {
                        connected.set(false);
                        LOGGER.error("An error occured while connecting to sse endpoint : ", e);
                        CompletableFuture.delayedExecutor(5, SECONDS, executorService).execute(() -> doConnect(request, consumer, id));
                        throw new RuntimeException(e);
                    });
            return queryFuture;
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> disconnect() {
        LOGGER.info("Disconnecting from SSE endpoint");
        if (Objects.nonNull(currentConnection)) {
            LOGGER.debug("Closing event stream");
            currentConnection.close();
        }

        connected.set(false);
        return CompletableFuture.completedFuture(null);
    }

    public void close() {
        disconnect();
        lifeProbeExecutorService.shutdown();
        executorService.shutdown();
    }


    @FunctionalInterface
    public interface ReconnectionConsumer {
        void apply(Long connectionId, Long eventId, IzanamiEvent event);
    }
    
    // TODO factorise
    public CompletableFuture<Void> reconnect() {
        LOGGER.debug("Reconnecting with new feature set...");
        if (Objects.nonNull(currentConnection)) {
            LOGGER.debug("Disconnecting");
            disconnect();
        } else {
            LOGGER.debug("No connection opened");
        }

        LOGGER.debug("Reconnecting");
        return doConnect(request, consumer, connectionId.get());
    }

    public CompletableFuture<Void> reconnectWith(FeatureRequest request, ReconnectionConsumer consumer) {
        long nextId = connectionId.incrementAndGet();
        this.request = request;
        this.consumer = (evtId, evt) -> consumer.apply(nextId, evtId, evt);

        return reconnect();
    }

    public static class EventService {
        public static final Logger LOGGER = LoggerFactory.getLogger(EventService.class);

        public static Optional<IzanamiEvent> fromSSE(ServerSentEvent event) {
            try {
                JsonNode json = ResponseUtils.mapper.readTree(event.data);
                switch (event.eventType) {
                    case "FEATURE_CREATED":
                        return ResponseUtils.parseFeature(json.get("id").asText(), (ObjectNode) json.get("payload")).map(IzanamiEvent.FeatureCreated::new);
                    case "FEATURE_DELETED":
                        String id = json.get("payload").asText();
                        return Optional.of(new IzanamiEvent.FeatureDeleted(id));
                    case "FEATURE_UPDATED":
                        return ResponseUtils.parseFeature(json.get("id").asText(), (ObjectNode) json.get("payload")).map(IzanamiEvent.FeatureUpdated::new);
                    case "FEATURE_STATES":
                        return Optional.of(new IzanamiEvent.FeatureStates(ResponseUtils.parseFeatureResponse(json.get("payload").toString()).value));
                    default:
                        return Optional.empty();
                }
            } catch (JsonProcessingException e) {
                LOGGER.error("Failed to parse SSE " + event + "with error", e);
                return Optional.empty();
            }
        }
    }

    public static class SSEStateMachine {
        private final Pattern EVENT_LINE_REGEXP = Pattern.compile("^(?<header>event|id|data)( *):( *)(?<value>.*)$", Pattern.CASE_INSENSITIVE);
        private ServerSentEvent.Builder currentBuilder = ServerSentEvent.newBuilder();

        public Optional<ServerSentEvent> addLine(String line) {
            LOGGER.info("LINE {}", line);
            if (Objects.isNull(line) || line.isBlank()) {
                var sse = currentBuilder.build();
                this.currentBuilder = ServerSentEvent.newBuilder();
                if(Objects.nonNull(sse.data)) {
                    LOGGER.debug("Received {}", sse);
                    return Optional.of(sse);
                }
                return Optional.empty();
            } else {
                processLine(line);
                return Optional.empty();
            }
        }

        private void processLine(String line) {
            var eventMatcher = EVENT_LINE_REGEXP.matcher(line);
            if (eventMatcher.matches()) {
                String lineType = eventMatcher.group("header");
                String value = eventMatcher.group("value");
                switch (lineType.toLowerCase()) {
                    case "data": {
                        this.currentBuilder.withData(value);
                        break;
                    }
                    case "id": {
                        this.currentBuilder.withId(value);
                        break;
                    }
                    case "event": {
                        this.currentBuilder.withEventType(value);
                        break;
                    }
                    default: {

                    }
                }
            }
        }
    }

    public static class ServerSentEvent {
        public final String eventType;
        public final String data;
        public final String id;
        public final Long retry;

        private ServerSentEvent(Builder builder) {
            eventType = builder.eventType;
            data = builder.data;
            id = builder.id;
            retry = builder.retry;
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public static final class Builder {
            private String eventType;
            private String data;
            private String id;
            private Long retry;

            private Builder() {
            }

            public Builder withEventType(String val) {
                eventType = val;
                return this;
            }

            public Builder withData(String val) {
                data = val;
                return this;
            }

            public Builder withId(String val) {
                id = val;
                return this;
            }

            public Builder withRetry(Long val) {
                retry = val;
                return this;
            }

            public ServerSentEvent build() {
                return new ServerSentEvent(this);
            }
        }

        @Override
        public String toString() {
            return "ServerSentEvent{" +
                    "eventType='" + eventType + '\'' +
                    ", data='" + data + '\'' +
                    ", id='" + id + '\'' +
                    ", retry=" + retry +
                    '}';
        }
    }
}
