package fr.maif.requests;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fr.maif.ClientConfiguration;
import fr.maif.errors.IzanamiError;
import fr.maif.features.Feature;
import fr.maif.features.results.IzanamiResult;
import fr.maif.features.results.IzanamiResult.Result;
import fr.maif.features.results.IzanamiResult.Success;
import fr.maif.features.values.FeatureValue;
import fr.maif.requests.events.IzanamiEvent;
import fr.maif.requests.events.SSEClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class SSEFeatureService implements FeatureService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SSEFeatureService.class);
    private final SSEClient sseClient;
    private final Cache<String, Feature> cache;
    private FeatureRequest scope = FeatureRequest.newFeatureRequest();
    private FetchFeatureService underlying;
    private ClientConfiguration configuration;

    public SSEFeatureService(ClientConfiguration clientConfiguration) {
        this.configuration = clientConfiguration;
        this.sseClient = new SSEClient(clientConfiguration);
        this.cache = Caffeine.newBuilder().build();
        this.underlying = new FetchFeatureService(clientConfiguration);
    }

    private void processEvent(IzanamiEvent event) {
        LOGGER.debug("Processing event {}", event);
        if (event instanceof IzanamiEvent.FeatureStates) {
            cache.invalidateAll();
            var featureStates = (IzanamiEvent.FeatureStates) event;
            featureStates.features.forEach(cache::put);
            LOGGER.debug("Done updating cache with feature states");
        } else if (event instanceof IzanamiEvent.FeatureCreated) {
            var featureCreated = (IzanamiEvent.FeatureCreated) event;
            cache.put(featureCreated.feature.id, featureCreated.feature);
        } else if (event instanceof IzanamiEvent.FeatureUpdated) {
            var featureUpdated = (IzanamiEvent.FeatureUpdated) event;
            cache.put(featureUpdated.feature.id, featureUpdated.feature);
        } else if (event instanceof IzanamiEvent.FeatureDeleted) {
            cache.invalidate(((IzanamiEvent.FeatureDeleted) event).feature);
        }
    }

    public CompletableFuture<Void> disconnect() {
        this.sseClient.close();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<IzanamiResult> featureValues(FeatureRequest request) {
        LOGGER.debug("Feature states is requested for {}", String.join(",", request.features.keySet()));
        Set<SpecificFeatureRequest> missingFeatures = new HashSet<>();
        Set<SpecificFeatureRequest> scriptFeatures = new HashSet<>();
        Map<String, IzanamiResult.Result> activation = new ConcurrentHashMap<>();

        request.features.values().forEach(f -> {
            var maybeFeature = cache.getIfPresent(f.feature);
            if (Objects.isNull(maybeFeature)) {
                LOGGER.debug("Feature {} is absent from cache", f.feature);
                if (!scope.features.containsKey(f.feature)) {
                    LOGGER.debug("Feature {} is not in scope, adding it", f.feature);
                    missingFeatures.add(f);
                }
            } else {
                maybeFeature.value(request.context.orElse(null), request.user).ifPresentOrElse(active -> {
                    LOGGER.debug("Computing activation for {} from cache, result is {}", f.feature, active);
                    activation.put(f.feature, new Success((FeatureValue) active));
                }, () -> scriptFeatures.add(f));
            }
        });

        LOGGER.debug("Activation is {}", activation);

        if (LOGGER.isDebugEnabled() && !missingFeatures.isEmpty()) {
            LOGGER.debug("Missing features in cache and current scope : {}", missingFeatures.stream().map(f -> f.feature).collect(Collectors.joining(",")));
        } else if(LOGGER.isDebugEnabled()) {
            LOGGER.debug("No missing features in cache");
        }

        List<CompletableFuture<Map<String, Result>>> results = new ArrayList<>();
        if (!missingFeatures.isEmpty()) {
            CompletableFuture<Map<String, Result>> missingFuture = new CompletableFuture<>();
            Duration timeout = request.getTimeout().orElse(configuration.callTimeout);
            missingFuture.completeOnTimeout(new HashMap<>(), timeout.getSeconds(), TimeUnit.SECONDS);

            results.add(missingFuture);
            AtomicBoolean isFirst = new AtomicBoolean(true);
            var newScope = Optional.ofNullable(this.scope)
                    .map(s -> s.copy().withSpecificFeatures(missingFeatures).withContext(request.context.orElse(null)).withUser(request.user).withPayload(request.payload.orElse(null)))
                    .orElseGet(() -> request);
            this.scope = newScope;

            LOGGER.debug("Requesting {} missing features", missingFeatures.size());
            this.sseClient.reconnectWith(newScope, (connId, evtId, event) -> {
                LOGGER.debug("Received {} event in client", event.getClass().getSimpleName());
                this.processEvent(event);
                if(isFirst.get() && event instanceof IzanamiEvent.FeatureStates && connId.equals(evtId)) {
                    LOGGER.debug("Receiving response for missing features");
                    isFirst.set(false);
                    var states = (IzanamiEvent.FeatureStates) event;
                    Map<String, IzanamiResult.Result> missingResults = new HashMap<>();
                    states.features.forEach((key, value) -> {
                        LOGGER.debug("Received {} for feature {}", value.active, key);
                        cache.put(key, value);
                        missingResults.put(key, new IzanamiResult.Success(value.active));
                    });

                    missingFuture.complete(missingResults);
                }
            }).exceptionally(e -> {
                LOGGER.error("Received exception while requesting missing features", e);
                missingFuture.complete(new HashMap<>());
                return null;
            });
        }

        // TODO there might be an optimization to do here, in case we got both missing and script features,
        // we would simply need to retrieve missingFeature query result to get our script feature status
        if (!scriptFeatures.isEmpty()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Some script feature are requested : {}", missingFeatures.stream().map(f -> f.feature).collect(Collectors.joining(",")));
            }

            results.add(underlying.featureValues(request.copy().clearFeatures().withSpecificFeatures(scriptFeatures)).thenApply(r -> r.results));
        }

        LOGGER.debug("Will wait for {} queries", results.size());
        return CompletableFuture.allOf(results.toArray(new CompletableFuture[0])).thenApply(useless -> {
            results.forEach(cf -> {
                var act = cf.join();
                act.entrySet().stream()
                        .filter(entry -> Objects.nonNull(entry.getValue()) && Objects.nonNull(entry.getKey()))
                        .forEach(entry -> activation.put(entry.getKey(), entry.getValue()));
            });
            // ConcurrentHashMap does not support null values, therefore we switch to a simple HashMap
            var activationWithMaybeNulls = new HashMap<String, Result>();
            request.features.values().stream().map(f -> f.feature).forEach(id -> {
                if (!activation.containsKey(id)) {
                    var errorStrategy = request.errorStrategyFor(id).orElseGet(() -> configuration.errorStrategy);
                    String errorMessage = "Missing feature in Izanami response : " + id + ". Either this feature has been deleted or your key is not authorized for it.";
                    activationWithMaybeNulls.put(id, new IzanamiResult.Error(errorStrategy, new IzanamiError(errorMessage)));
                } else {
                    activationWithMaybeNulls.put(id, activation.get(id));
                }
            });
            return new IzanamiResult(activationWithMaybeNulls, configuration.castStrategy, configuration.errorStrategy);
        });
    }

    @Override
    public ClientConfiguration configuration() {
        return configuration;
    }
}
