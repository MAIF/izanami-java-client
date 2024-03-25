package fr.maif.requests;

import fr.maif.FeatureClientErrorStrategy;

import java.util.Optional;

/**
 * Data container that can be used to specify a per feature error/cache strategy for a given query.
 */
public class SpecificFeatureRequest {
    public final String feature;
    public Optional<Boolean> ignoreCache = Optional.empty();
    public Optional<FeatureClientErrorStrategy<?>> errorStrategy = Optional.empty();

    private SpecificFeatureRequest(String feature) {
        this.feature = feature;
    }

    /**
     * Feature id to query
     * @param feature feature id
     * @return new specific feature request with provided id
     */
    public static SpecificFeatureRequest feature(String feature) {
        return new SpecificFeatureRequest(feature);
    }

    /**
     * Whether cache should be ignored this feature
     * @param ignoreCache if cache should be ignored
     * @return this object updated with given cache strategy
     */
    public SpecificFeatureRequest ignoreCache(boolean ignoreCache) {
        this.ignoreCache = Optional.of(ignoreCache);
        return this;
    }

    /**
     * Error strategy to use for this query
     * @param errorStrategy error strategy to use
     * @return this object updated with given error strategy
     */
    public SpecificFeatureRequest withErrorStrategy(FeatureClientErrorStrategy<?> errorStrategy) {
        this.errorStrategy = Optional.ofNullable(errorStrategy);
        return this;
    }

}
