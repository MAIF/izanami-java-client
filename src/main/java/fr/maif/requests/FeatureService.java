package fr.maif.requests;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fr.maif.ClientConfiguration;
import fr.maif.errors.IzanamiError;
import fr.maif.features.Feature;
import fr.maif.http.HttpRequester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static fr.maif.requests.FeatureRequest.newFeatureRequest;

public class FeatureService {
    protected ClientConfiguration configuration;
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureService.class);
    private final Cache<String, Feature> cache;

    public FeatureService(ClientConfiguration configuration) {
        this.configuration = configuration;
        this.cache = Caffeine.newBuilder()
                .build();


        if(configuration.cacheConfiguration.enabled) {
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(
                    this::refreshCache,
                    0,
                    configuration.cacheConfiguration.refreshInterval.getSeconds(), TimeUnit.SECONDS
            );
        }
    }

    private void putValuesInCache(Map<String, Feature> features) {
        features.forEach(cache::put);
    }

    private void refreshCache() {
        Set<String> features = cache.asMap().keySet();
        LOGGER.debug("Refreshing cache for {}", String.join(",", features));
        if(features.isEmpty()) {
            return;
        }
        var request = new FeatureRequest().withFeatures(features);

        HttpRequester
                .performRequest(configuration, request)
                .thenAccept(result -> {
                    if(!result.isError()) {
                        LOGGER.debug("Received following features for cache refresh {}", String.join("," + result.value.entrySet()));
                        cache.invalidateAll();
                        putValuesInCache(result.value);
                    } else {
                        LOGGER.error("Failed to refresh cache : {}", result.error.get());
                    }
                });
    }

    public CompletableFuture<Map<String, Boolean>> featureStates(
            FeatureRequest request
    ) {
        LOGGER.debug("Feature activation request for {}", String.join(",", request.features.keySet()));
        Set<SpecificFeatureRequest> missingFeatures = new HashSet<>();
        Map<String, Boolean> activation  = new HashMap<>();

        request.features.values()
                .forEach(f -> {
                    boolean shouldIgnoreCache = request.isCacheIgnoredFor(f.feature)
                            .orElseGet(() -> !configuration.cacheConfiguration.enabled);
                    if(shouldIgnoreCache) {
                        missingFeatures.add(f);
                    } else {
                        Optional<Boolean> maybeActivation = Optional.ofNullable(cache.getIfPresent(f.feature))
                                .flatMap(cachedFeature -> cachedFeature.active(request.context.orElse(null), request.user));
                        maybeActivation.ifPresentOrElse(active -> activation.put(f.feature, active), () -> missingFeatures.add(f));
                    }
                });

        if(missingFeatures.isEmpty()) {
            return CompletableFuture
                    .completedFuture(activation);
        } else {
            var missingRequest = newFeatureRequest().withSpecificFeatures(missingFeatures)
                    .withErrorStrategy(request.errorStrategy.orElse(null))
                    .withCallTimeout(request.getTimeout().orElse(null))
                    .withUser(request.user)
                    .withContext(request.context.orElse(null))
                    .withPayload(request.payload.orElse(null));
            return HttpRequester.performRequest(configuration, missingRequest)
                    .thenApply(featureResponse -> {
                        if(featureResponse.isError()) {
                            String errorMsg = featureResponse.error.get();
                            LOGGER.error("Failed to retrieve features : {}", errorMsg);
                            missingFeatures.forEach(f -> {
                                var errorStrategy = missingRequest.errorStrategyFor(f.feature).orElseGet(() -> configuration.errorStrategy);
                                if(!errorStrategy.lastKnownFallbackAllowed) {
                                    activation.put(f.feature, errorStrategy.handleError(new IzanamiError(errorMsg)).join());
                                } else {
                                    Boolean active = Optional.ofNullable(cache.getIfPresent(f.feature))
                                            .flatMap(feat -> feat.active(missingRequest.context.orElse(null), missingRequest.user))
                                            .orElseGet(() -> errorStrategy.handleError(new IzanamiError(errorMsg)).join());
                                    activation.put(f.feature, active);
                                }
                            });
                        } else {
                            Map<String, Feature> featuresById = featureResponse.value;
                            missingFeatures.forEach(f -> {
                                if(featuresById.containsKey(f.feature)) {
                                    var feature = featuresById.get(f.feature);
                                    cache.put(f.feature, feature);
                                    activation.put(f.feature, feature.active);
                                } else {
                                    // TODO deduplicate this
                                    var errorStrategy = request.errorStrategyFor(f.feature).orElseGet(() -> configuration.errorStrategy);
                                    String errorMessage = "Missing feature in Izanami response : " + f.feature +". Either this feature has been deleted or your key is not authorized for it.";
                                    if(!errorStrategy.lastKnownFallbackAllowed) {
                                        activation.put(f.feature, errorStrategy.handleError(new IzanamiError(errorMessage)).join());
                                    } else {
                                        Boolean active = Optional.ofNullable(cache.getIfPresent(f.feature))
                                                .flatMap(feat -> feat.active(request.context.orElse(null), request.user))
                                                .orElseGet(() -> errorStrategy.handleError(new IzanamiError(errorMessage)).join());
                                        activation.put(f.feature, active);
                                    }
                                }
                            });
                        }
                        return activation;
                    }).exceptionally(ex -> {
                        LOGGER.error("Failed to query remote Izanami", ex);
                        missingFeatures.forEach(f -> {
                            // TODO deduplicate this
                            var errorStrategy = request.errorStrategyFor(f.feature).orElseGet(() -> configuration.errorStrategy);
                            String errorMessage = "Missing feature in Izanami response : " + f.feature +". Either this feature has been deleted or your key is not authorized for it.";
                            if(!errorStrategy.lastKnownFallbackAllowed) {
                                activation.put(f.feature, errorStrategy.handleError(new IzanamiError(errorMessage)).join());
                            } else {
                                Boolean active = Optional.ofNullable(cache.getIfPresent(f.feature))
                                        .flatMap(feat -> feat.active(request.context.orElse(null), request.user))
                                        .orElseGet(() -> errorStrategy.handleError(new IzanamiError(errorMessage)).join());
                                activation.put(f.feature, active);
                            }
                        });
                        return activation;
                    });
        }
    }

    public CompletableFuture<Boolean> featureStates(SingleFeatureRequest request) {
        return featureStates(request.toActivationRequest())
                .thenApply(resp -> resp.get(request.feature));
    }
}
