package fr.maif.requests;

import fr.maif.FeatureClientErrorStrategy;
import fr.maif.features.values.BooleanCastStrategy;

import java.time.Duration;
import java.util.Optional;

/**
 * This class is a data container used to represent query for a single feature
 */
public class SingleFeatureRequest {
    private FeatureRequest request = FeatureRequest.newFeatureRequest();
    String feature;

    /**
     * Constructor
     * @param feature feature id to query
     */
    public SingleFeatureRequest(String feature) {
        this.feature = feature;
    }

    /**
     * Create a new single feature request with provided feature id
     * @param feature feature id to query
     * @return a new SingleFeatureRequest initialized with given id
     */
    public static SingleFeatureRequest newSingleFeatureRequest(String feature) {
        return new SingleFeatureRequest(feature);
    }

    /**
     * Add or update user for this request
     * @param val user to use for this request
     * @return this request modified with provided user
     */
    public SingleFeatureRequest withUser(String val) {
        request.user = val;
        return this;
    }

    /**
     * Add or update http call timeout for this request
     * @param timeout http call timeout to use for this request
     * @return this request modified with provided http call timeout
     */
    public SingleFeatureRequest withCallTimeout(Duration timeout) {
        request.callTimeout = Optional.ofNullable(timeout);
        return this;
    }

    /**
     * Add or update error strategy for this request
     * @param errorStrategy error strategy to use for this request
     * @return this request modified with provided error strategy
     */
    public SingleFeatureRequest withErrorStrategy(FeatureClientErrorStrategy errorStrategy) {
        request.withErrorStrategy(errorStrategy);
        return this;
    }

    /**
     * Context to use for this request
     * @param context context to use
     * @return this request modified with given context
     */
    public SingleFeatureRequest withContext(String context) {
        request.withContext(context);
        return this;
    }

    public SingleFeatureRequest withPayload(String payload) {
        request.withPayload(payload);
        return this;
    }

    public SingleFeatureRequest withBooleanCastStrategy(BooleanCastStrategy castStrategy) {
        request.withBooleanCastStrategy(castStrategy);
        return this;
    }

    /**
     * Whether this request should ignore cache
     * @param shouldIgnoreCache indicate if this feature should ignore cache
     * @return this request modified with cache use indication
     */
    public SingleFeatureRequest ignoreCache(boolean shouldIgnoreCache) {
        request.ignoreCache(shouldIgnoreCache);
        return this;
    }

    public FeatureRequest toActivationRequest() {
        return request.withFeatures(feature);
    }
}
