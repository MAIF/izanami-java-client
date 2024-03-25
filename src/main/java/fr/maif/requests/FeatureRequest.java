package fr.maif.requests;

import fr.maif.errors.IzanamiException;
import fr.maif.FeatureClientErrorStrategy;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Data container used to represent query for multiple features
 */
public class FeatureRequest {
    Map<String, SpecificFeatureRequest> features = new HashMap<>();
    Optional<FeatureClientErrorStrategy<?>> errorStrategy = Optional.empty();
    Optional<Boolean> ignoreCache = Optional.empty();
    Optional<String> context = Optional.empty();

    Optional<Duration> callTimeout = Optional.empty();
    Optional<String> payload = Optional.empty();
    String user = "";

    /**
     * Create a new multi feature request
     * @return a new request
     */
    public static FeatureRequest newFeatureRequest() {
        return new FeatureRequest();
    }

    public static SingleFeatureRequest newSingleFeatureRequest(String feature) {
        return new SingleFeatureRequest(feature);
    }

    /**
     * Add or update user for this request
     * @param val user to use for this request
     * @return this request modified with provided user
     */
    public FeatureRequest withUser(String val) {
        user = val;
        return this;
    }

    /**
     * Add or update error strategy for this request
     * @param errorStrategy error strategy to use for this request
     * @return this request modified with provided error strategy
     */
    public FeatureRequest withErrorStrategy(FeatureClientErrorStrategy<?> errorStrategy) {
        this.errorStrategy = Optional.ofNullable(errorStrategy);
        return this;
    }

    /**
     * Add or update http call timeout for this request
     * @param timeout http call timeout to use for this request
     * @return this request modified with provided http call timeout
     */
    public FeatureRequest withCallTimeout(Duration timeout) {
        this.callTimeout = Optional.ofNullable(timeout);
        return this;
    }

    /**
     * Add provided features for this request
     * @param val features to add to this request
     * @return this request modified new features
     */
    public FeatureRequest withFeatures(Set<String> val) {
        return this.withSpecificFeatures(
                val.stream()
                .map(SpecificFeatureRequest::feature)
                .collect(Collectors.toSet())
        );
    }

    /**
     * Add provided features for this request
     * @param val features to add to this request
     * @return this request modified new features
     */
    public FeatureRequest withSpecificFeatures(Set<SpecificFeatureRequest> val) {
        val.forEach(feature -> {
            features.put(feature.feature, feature);
        });
        return this;
    }


    /**
     * Add provided features for this request
     * @param features features to add to this request
     * @return this request modified new features
     */
    public FeatureRequest withFeatures(String... features) {
        return this.withSpecificFeatures(Arrays.stream(features)
                .map(SpecificFeatureRequest::feature)
                .collect(Collectors.toSet()));
    }

    /**
     * Add provided features for this request
     * @param features features to add to this request
     * @return this request modified new features
     */
    public FeatureRequest withFeatures(SpecificFeatureRequest... features) {
        return this.withSpecificFeatures(Arrays.stream(features).collect(Collectors.toSet()));
    }

    /**
     * Add provided features for this request
     * @param features features to add to this request
     * @return this request modified new features
     */
    public FeatureRequest withFeature(SpecificFeatureRequest features) {
        this.features.put(features.feature, features);
        return this;
    }

    /**
     * Add provided feature for this request
     * @param feature features to add to this request
     * @return this request modified new feature
     */
    public FeatureRequest withFeature(String feature) {
        return this.withFeature(SpecificFeatureRequest.feature(feature));
    }

    /**
     * Whether this request should ignore cache
     * @param ignoreCache indicate if this feature should ignore cache
     * @return this request modified with cache use indication
     */
    public FeatureRequest ignoreCache(boolean ignoreCache) {
        this.ignoreCache = Optional.of(ignoreCache);
        return this;
    }

    /**
     * Context to use for this request
     * @param context context to use
     * @return this request modified with given context
     */
    public FeatureRequest withContext(String context) {
        this.context = Optional.ofNullable(context);
        return this;
    }

    public FeatureRequest withPayload(String payload) {
        this.payload = Optional.ofNullable(payload);
        return this;
    }

    Optional<Boolean> isCacheIgnoredFor(String feature) {
        if(!features.containsKey(feature)) {
            throw new IzanamiException("Feature " + feature + " is not present in request.");
        }
        var specificFeature = features.get(feature);
        return specificFeature.ignoreCache
                .or(() -> this.ignoreCache);
    }

    Optional<FeatureClientErrorStrategy<?>> errorStrategyFor(String feature) {
        if(!features.containsKey(feature)) {
            throw new IzanamiException("Feature " + feature + " is not present in request.");
        }
        var specificFeature = features.get(feature);
        return specificFeature.errorStrategy
                .or(() -> this.errorStrategy);
    }

    /**
     * Feature ids for this request
     * @return String feature ids for this request
     */
    public Set<String> getFeatures() {
        return features.keySet();
    }

    /**
     * User for this request
     * @return user for this request
     */
    public String getUser() {
        return user;
    }

    /**
     * Context for this request
     * @return An optional indicating context used for this request (if any)
     */
    public Optional<String> getContext() {
        return context;
    }

    /**
     * Http call timeout for this request
     * @return An optional indicating http call timeout to use for this request (if any)
     */
    public Optional<Duration> getTimeout() {
        return callTimeout;
    }

    public Optional<String> getPayload() {
        return payload;
    }
}
