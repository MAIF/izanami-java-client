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
import java.util.concurrent.atomic.AtomicInteger;
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

    /** Incremented by reconnectWith() only — used by SSEFeatureService to match events to the right connection. */
    private final AtomicLong connectionId = new AtomicLong(0L);
    /** Incremented on every reconnect() — used to detect stale delayed reconnects and stale thenAccept callbacks. */
    private final AtomicLong reconnectGeneration = new AtomicLong(0L);
    /** Tracks consecutive failed reconnect attempts for exponential backoff (5s, 10s, 20s, 40s, 60s cap). */
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final Object connectionLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** The root sendAsync future — stored separately from queryFuture so disconnect() can cancel the HTTP exchange itself. */
    private CompletableFuture<HttpResponse<Stream<String>>> rawFuture;
    /** The terminal future of the chain (rawFuture -> thenAccept -> exceptionally). */
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
                        } else {
                            // Connection healthy for a full life probe cycle —
                            // safe to reset exponential backoff for future reconnects.
                            reconnectAttempts.set(0);
                        }
                    }
                },
                maxToleratedDurationWithoutEvents.getSeconds(),
                maxToleratedDurationWithoutEvents.getSeconds(),
                SECONDS
        );
        this.executorService = Executors.newFixedThreadPool(2);
        this.httpClient = createHttpClient();
    }

    /**
     * Creates a fresh HttpClient. HTTP/1.1 is forced because the JDK's HTTP/2
     * implementation has known bugs with long-lived SSE streams: streams are not
     * properly released on close, leading to "too many concurrent streams" errors.
     * connectTimeout covers the TCP handshake only, not the SSE stream lifetime.
     */
    private HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .executor(this.executorService)
                // TODO make connectTimeout configurable via ClientConfiguration
                .connectTimeout(Duration.ofSeconds(10))
                .build();
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
                    .setHeader("Izanami-Client-Secret", clientConfiguration.connectionInformation.clientSecret);

            Duration responseTimeout = request.getTimeout().orElse(clientConfiguration.callTimeout);

            var r = request.getPayload()
                    .map(payload -> requestBuilder.POST(HttpRequest.BodyPublishers.ofString(payload)))
                    .orElseGet(requestBuilder::GET).build();

            LOGGER.debug("Calling {} with response timeout of {} seconds", r.uri().toString(), responseTimeout.toSeconds());

            long myGeneration = reconnectGeneration.get();

            this.rawFuture = httpClient.sendAsync(r, HttpResponse.BodyHandlers.ofLines());
            this.queryFuture = this.rawFuture
                    // orTimeout scopes the timeout to the initial HTTP response only.
                    // Once thenAccept starts (response received), the SSE stream runs
                    // indefinitely — orTimeout does not affect it.
                    .orTimeout(responseTimeout.toSeconds(), TimeUnit.SECONDS)
                    .thenAccept(resp -> {

                        // Guard: if reconnect() was called since this doConnect(),
                        // this connection is stale — don't install it
                        if (reconnectGeneration.get() != myGeneration) {
                            LOGGER.debug("Stale connection detected, discarding response");
                            resp.body().close();
                            return;
                        }

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
                                    // Update lastEventDate for any complete SSE event
                                    // (including keepalives with unrecognized event types)
                                    // so the life probe knows the connection is alive.
                                    res.ifPresent(sse -> lastEventDate.set(LocalDateTime.now()));
                                    return res.flatMap(EventService::fromSSE);
                                })
                                .flatMap(Optional::stream)
                                .forEach(evt -> {
                                    consumer.accept(id, evt);
                                });
                    }).exceptionally(e -> {
                        connected.set(false);
                        if (closed.get()) {
                            LOGGER.debug("SSE client is closed, not reconnecting");
                            return null;
                        }
                        if (e instanceof CancellationException ||
                            (e.getCause() instanceof CancellationException)) {
                            LOGGER.debug("SSE connection was intentionally cancelled");
                            return null;
                        }

                        // Transient errors are network-level failures expected to self-heal:
                        //   IOException/UncheckedIOException — connection drop, network blip, RST
                        //   TimeoutException — server slow to respond
                        // Permanent errors are server-side rejections (401, 403, 404, 500)
                        // that won't resolve without a config or server-side change.
                        //
                        // Both schedule a reconnect (server errors may be temporary),
                        // but only permanent errors are propagated to the caller so it
                        // can apply its error strategy immediately. Transient errors
                        // return null and let the reconnect deliver data silently.
                        Throwable cause = (e instanceof CompletionException && e.getCause() != null) ? e.getCause() : e;
                        boolean isTransient = cause instanceof java.io.IOException
                                || cause instanceof java.io.UncheckedIOException
                                || cause instanceof java.util.concurrent.TimeoutException;

                        long myGen = reconnectGeneration.get();
                        long delay = Math.min(5 * (1L << Math.min(reconnectAttempts.getAndIncrement(), 4)), 60);
                        LOGGER.warn("SSE connection lost (transient={}), will reconnect in {}s: {}", isTransient, delay, e.getMessage());
                        CompletableFuture.delayedExecutor(delay, SECONDS, executorService)
                                .execute(() -> {
                                    if (closed.get()) {
                                        LOGGER.debug("SSE client closed, skipping reconnect");
                                    } else if (reconnectGeneration.get() == myGen) {
                                        reconnect();
                                    } else {
                                        LOGGER.debug("Skipping stale reconnect, connection already refreshed");
                                    }
                                });

                        if (isTransient) {
                            return null;
                        }
                        // Permanent failure — propagate so caller can react,
                        // but reconnect is still scheduled above
                        throw (e instanceof CompletionException) ? (CompletionException) e : new CompletionException(e);
                    });
            return queryFuture;
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> disconnect() {
        synchronized (connectionLock) {
            LOGGER.info("Disconnecting from SSE endpoint");

            // Cancel futures FIRST:
            // 1. rawFuture.cancel() — if HTTP response hasn't arrived yet,
            //    this prevents thenAccept from ever executing (no leaked stream reader)
            // 2. queryFuture.cancel() — completes the terminal stage, so the
            //    exceptionally handler doesn't fire with IOException when close()
            //    unblocks the reading thread
            if (Objects.nonNull(rawFuture)) {
                rawFuture.cancel(true);
                rawFuture = null;
            }

            if (Objects.nonNull(queryFuture)) {
                queryFuture.cancel(true);
                queryFuture = null;
            }

            if (Objects.nonNull(currentConnection)) {
                LOGGER.debug("Closing event stream");
                currentConnection.close();
                currentConnection = null;
            }

            connected.set(false);
            return CompletableFuture.completedFuture(null);
        }
    }

    public void close() {
        closed.set(true);
        disconnect();
        lifeProbeExecutorService.shutdown();
        executorService.shutdown();
    }


    @FunctionalInterface
    public interface ReconnectionConsumer {
        void apply(Long connectionId, Long eventId, IzanamiEvent event);
    }
    
    public CompletableFuture<Void> reconnect() {
        synchronized (connectionLock) {
            reconnectGeneration.incrementAndGet();
            LOGGER.debug("Reconnecting...");
            if (Objects.nonNull(currentConnection) || Objects.nonNull(rawFuture)) {
                LOGGER.debug("Disconnecting");
                disconnect();
            } else {
                LOGGER.debug("No connection opened");
            }

            LOGGER.debug("Reconnecting");
            return doConnect(request, consumer, connectionId.get());
        }
    }

    public CompletableFuture<Void> reconnectWith(FeatureRequest request, ReconnectionConsumer consumer) {
        synchronized (connectionLock) {
            long nextId = connectionId.incrementAndGet();
            this.request = request;
            this.consumer = (evtId, evt) -> consumer.apply(nextId, evtId, evt);

            return reconnect();
        }
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
            LOGGER.debug("LINE {}", line);
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
