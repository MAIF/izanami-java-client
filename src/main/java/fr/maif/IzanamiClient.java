package fr.maif;

import fr.maif.http.IzanamiHttpClient;
import fr.maif.requests.FeatureRequest;
import fr.maif.requests.FeatureService;
import fr.maif.requests.IzanamiConnectionInformation;
import fr.maif.requests.SingleFeatureRequest;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Client to query Izanami for feature activation status.
 * This should be instantiated only once by application.
 */
public class IzanamiClient {
    private final ClientConfiguration configuration;
    private final FeatureService featureService;
    private CompletableFuture<Void> loader;

    public IzanamiClient(
            IzanamiConnectionInformation connectionInformation,
            Optional<FeatureClientErrorStrategy> errorStrategy,
            Optional<FeatureCacheConfiguration> cacheConfiguration,
            Optional<IzanamiHttpClient> httpClient,
            Optional<Duration> duration,
            Set<String> idsToPreload
    ) {
        this.configuration = new ClientConfiguration(
                connectionInformation,
                errorStrategy.orElseGet(FeatureClientErrorStrategy::nullValueStrategy),
                cacheConfiguration.orElseGet(() -> FeatureCacheConfiguration.newBuilder().enabled(false).build()),
                httpClient.orElseGet(IzanamiHttpClient.DefaultIzanamiHttpClient::new),
                duration.orElse(Duration.ofSeconds(10L))
        );

        this.featureService = new FeatureService(configuration);

        if(Objects.nonNull(idsToPreload) && !idsToPreload.isEmpty()) {
            this.loader = featureService.featureStates(FeatureRequest.newFeatureRequest().withFeatures(idsToPreload)).thenAccept(v -> {});
        } else {
            this.loader = CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Create a new izanami client builder
     * @param connectionInformation must contain connection information for your Izanami instance
     * @return an initialized new izanami client builder
     */
    public static IzanamiClientBuilder newBuilder(IzanamiConnectionInformation connectionInformation) {
        return new IzanamiClientBuilder(connectionInformation);
    }

    /**
     * Retrieve activation status for features in given request.
     * @param request request containing feature ids to query and optionally more information about query, cache use or error strategy.
     * @return a CompletableFuture containing a Map that associates each feature to its activation status
     */
    public CompletableFuture<Map<String, Boolean>> checkFeatureActivations(FeatureRequest request) {
        return featureService.featureStates(request);
    }

    /**
     * Retrieve activation status for a single feature
     * @param request containing feature id to query and optionally more information about query, cache use or error strategy.
     * @return a CompletableFuture containing activation status for requested feature
     */
    public CompletableFuture<Boolean> checkFeatureActivation(SingleFeatureRequest request) {
        return featureService.featureStates(request);
    }

    public CompletableFuture<Void> isLoaded() {
        return loader;
    }


    public static class IzanamiClientBuilder {
        private final IzanamiConnectionInformation connectionInformation;
        private Optional<FeatureClientErrorStrategy> errorStrategy = Optional.empty();
        private Optional<FeatureCacheConfiguration> cacheConfiguration = Optional.empty();
        private Optional<IzanamiHttpClient> client = Optional.empty();
        private Optional<Duration> callTimeout = Optional.empty();
        private Set<String> idsToPreload = Collections.emptySet();

        private IzanamiClientBuilder(IzanamiConnectionInformation connectionInformation) {
            this.connectionInformation = connectionInformation;
        }

        /**
         * Specify error strategy to use for this client. This strategy can be overridden both at query and query feature levels.
         * @param errorStrategy error strategy to use when activation status can't be retrieved / computed for a feature
         * @return updated builder
         */
        public IzanamiClientBuilder withErrorStrategy(FeatureClientErrorStrategy errorStrategy) {
            this.errorStrategy = Optional.ofNullable(errorStrategy);
            return this;
        }

        /**
         * Specify call timeout to use for default HTTP client. This indicates duration after which call to remote Izanami will timeout.
         * @param duration timeout before failing remote izanami query
         * @return updated builder
         */
        public IzanamiClientBuilder withCallTimeout(Duration duration) {
            this.callTimeout = Optional.ofNullable(duration);
            return this;
        }

        /**
         * Specify cache configuration to use fot this client. Cache behaviour may bo overridden both at query and query feature levels.
         * @param cacheConfiguration cache configuration
         * @return updated builder
         */
        public IzanamiClientBuilder withCacheConfiguration(FeatureCacheConfiguration cacheConfiguration) {
            this.cacheConfiguration = Optional.ofNullable(cacheConfiguration);
            return this;
        }

        /**
         * Specify custom http client to use for this client. this may be usefull if you need to a MTLS or proxy configuration on your izanami calls.
         * @param client custom client to use
         * @return updated builder
         */
        public IzanamiClientBuilder withCustomClient(IzanamiHttpClient client) {
            this.client = Optional.ofNullable(client);
            return this;
        }

        /**
         * Indicate feature to preload
         * @param ids ids of feature to preload
         * @return updated builder
         */
        public IzanamiClientBuilder withPreloadedFeatures(Set<String> ids) {
            this.idsToPreload = ids;
            return this;
        }

        /**
         * Indicate feature to preload
         * @param ids ids of feature to preload
         * @return updated builder
         */
        public IzanamiClientBuilder withPreloadedFeatures(String... ids) {
            this.idsToPreload = Arrays.stream(ids).collect(Collectors.toSet());
            return this;
        }

        /**
         * Build izanami client with this builder current information
         * @return a new izanami client
         */
        public IzanamiClient build() {
            return new IzanamiClient(
                    connectionInformation,
                    errorStrategy,
                    cacheConfiguration,
                    client,
                    callTimeout,
                    idsToPreload
            );
        }
    }
}