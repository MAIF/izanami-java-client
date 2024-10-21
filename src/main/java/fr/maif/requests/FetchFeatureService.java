package fr.maif.requests;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fr.maif.ClientConfiguration;
import fr.maif.errors.IzanamiError;
import fr.maif.features.Feature;
import fr.maif.features.results.IzanamiResult;
import fr.maif.features.results.IzanamiResult.Error;
import fr.maif.features.results.IzanamiResult.Result;
import fr.maif.features.results.IzanamiResult.Success;
import fr.maif.features.values.FeatureValue;
import fr.maif.features.values.NullValue;
import fr.maif.http.HttpRequester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static fr.maif.requests.FeatureRequest.newFeatureRequest;


public class FetchFeatureService implements FeatureService {
    protected ClientConfiguration configuration;
    private static final Logger LOGGER = LoggerFactory.getLogger(FetchFeatureService.class);
    private final Cache<String, Feature<?>> cache;

    public FetchFeatureService(ClientConfiguration configuration) {
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

    @Override
    public ClientConfiguration configuration() {
        return configuration;
    }

    @Override
    public CompletableFuture<IzanamiResult> featureValues(FeatureRequest request) {
        LOGGER.debug("Feature activation request for {}", String.join(",", request.features.keySet()));
        Set<SpecificFeatureRequest> missingFeatures = new HashSet<>();
        Map<String, Result> activation  = new HashMap<>();

        request.features.values()
                .forEach(f -> {
                    boolean shouldIgnoreCache = request.isCacheIgnoredFor(f.feature)
                            .orElseGet(() -> !configuration.cacheConfiguration.enabled);
                    if(shouldIgnoreCache) {
                        missingFeatures.add(f);
                    } else {
                       Optional<Feature> maybeCachedFeature = Optional.ofNullable(cache.getIfPresent(f.feature));
                       if(maybeCachedFeature.isEmpty()) {
                           missingFeatures.add(f);
                       } else {
                           var feature = maybeCachedFeature.get();
                           Optional<FeatureValue> value =  feature.value(request.context.orElse(null), request.user);
                           if(Objects.isNull(value)) {
                               // this is ugly, but we need to differentiate between a feature that is not present and a feature that is present but has null value
                               activation.put(f.feature, new Success(new NullValue()));
                           } else if(value.isEmpty()) {
                                 missingFeatures.add(f);
                            } else {
                                 activation.put(f.feature, new Success(value.get()));
                            }

                       }
                    }
                });
        if(LOGGER.isDebugEnabled()) {
            LOGGER.debug("Some features are missing in cache: {}", missingFeatures.stream().map(f -> f.feature).collect(Collectors.joining(",")));
        }

        if(missingFeatures.isEmpty()) {
            return CompletableFuture
                    .completedFuture(new IzanamiResult(activation, request.castStrategy.orElse(null), request.errorStrategy.orElse(configuration.errorStrategy)));
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
                                    activation.put(f.feature, new Error(errorStrategy, new IzanamiError(errorMsg)));
                                } else {
                                    Result res = Optional.ofNullable(cache.getIfPresent(f.feature))
                                            .flatMap(feat -> feat.value(missingRequest.context.orElse(null), missingRequest.user))
                                            .map(value -> {
                                                Result r = new Success(value);
                                                return r;
                                            })
                                            .orElseGet(() -> new Error(errorStrategy, new IzanamiError(errorMsg)));

                                    activation.put(f.feature, res);
                                }
                            });
                        } else {
                            Map<String, Feature> featuresById = featureResponse.value;
                            missingFeatures.forEach(f -> {
                                if(featuresById.containsKey(f.feature)) {
                                    var feature = featuresById.get(f.feature);
                                    cache.put(f.feature, feature);
                                    activation.put(f.feature, new Success(feature.active));
                                } else {
                                    // TODO deduplicate this
                                    var errorStrategy = request.errorStrategyFor(f.feature).orElseGet(() -> configuration.errorStrategy);
                                    String errorMessage = "Missing feature in Izanami response : " + f.feature +". Either this feature has been deleted or your key is not authorized for it.";
                                    if(!errorStrategy.lastKnownFallbackAllowed) {
                                        activation.put(f.feature, new Error(errorStrategy, new IzanamiError(errorMessage)));
                                    } else {
                                        Result res = Optional.ofNullable(cache.getIfPresent(f.feature))
                                                .flatMap(feat -> feat.value(request.context.orElse(null), request.user))
                                                .map(value -> {
                                                    Result r = new Success(value);
                                                    return r;
                                                })
                                                .orElseGet(() -> new Error(errorStrategy, new IzanamiError(errorMessage)));
                                        activation.put(f.feature, res);
                                    }
                                }
                            });
                        }
                        return new IzanamiResult(activation, request.castStrategy.orElse(configuration.castStrategy), request.errorStrategy.orElse(configuration.errorStrategy));
                    }).exceptionally(ex -> {
                        LOGGER.error("Failed to query remote Izanami", ex);
                        missingFeatures.forEach(f -> {
                            // TODO deduplicate this
                            var errorStrategy = request.errorStrategyFor(f.feature).orElseGet(() -> configuration.errorStrategy);
                            String errorMessage = "Missing feature in Izanami response : " + f.feature +". Either this feature has been deleted or your key is not authorized for it.";
                            if(!errorStrategy.lastKnownFallbackAllowed) {
                                activation.put(f.feature, new Error(errorStrategy, new IzanamiError(errorMessage)));
                            } else {
                            
                                Result res = Optional.ofNullable(cache.getIfPresent(f.feature))
                                .flatMap(feat -> feat.value(request.context.orElse(null), request.user))
                                    .map(value -> {
                                        Result r = new Success(value);
                                        return r;
                                    })
                                    .orElseGet(() -> new Error(errorStrategy, new IzanamiError(errorMessage)));

                                activation.put(f.feature, res);
                            }
                        });
                        return new IzanamiResult(activation, request.castStrategy.orElse(configuration.castStrategy), request.errorStrategy.orElse(configuration.errorStrategy));
                    });
        }
    }
}
