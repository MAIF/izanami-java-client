package fr.maif;

import fr.maif.features.results.IzanamiResult;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.http.IzanamiHttpClient;
import fr.maif.requests.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Client to query Izanami for feature activation status.
 * This should be instantiated only once by application.
 */
public class IzanamiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(IzanamiClient.class);
    private final ClientConfiguration configuration;
    private final FeatureService featureService;
    private CompletableFuture<Void> loader;

    @Deprecated
    public IzanamiClient(
            IzanamiConnectionInformation connectionInformation,
            Optional<FeatureClientErrorStrategy> errorStrategy,
            Optional<FeatureCacheConfiguration> cacheConfiguration,
            Optional<IzanamiHttpClient> httpClient,
            Optional<Duration> duration,
            Set<String> idsToPreload
    ) {
        this(connectionInformation, errorStrategy, cacheConfiguration, httpClient, duration, idsToPreload, Optional.empty());
    }

    /**
     * Constructor
     * @param connectionInformation information about remote Izanami instance
     * @param errorStrategy default error strategy to use in case client fails to fetch remote Izanami. This can be overrided at request level.
     * @param cacheConfiguration cache configuration to use
     * @param httpClient httpClient to use
     * @param callTimeout timeout for remote instance http calls
     * @param idsToPreload flag ids to preload, preloading id prevent from payin the cost of querying remote Izanami first time flags are needed
     * @param castStrategy default strategy to use to cast non-boolean values in boolean when needed. Possible values are STRICT (trying to cast non boolean value to boolean value will fail) and LAX (empty string, numeric 0 and null are false, everything else is true).
     */
    public IzanamiClient(
            IzanamiConnectionInformation connectionInformation,
            Optional<FeatureClientErrorStrategy> errorStrategy,
            Optional<FeatureCacheConfiguration> cacheConfiguration,
            Optional<IzanamiHttpClient> httpClient,
            Optional<Duration> callTimeout,
            Set<String> idsToPreload,
            Optional<BooleanCastStrategy> castStrategy
    ) {
        this.configuration = new ClientConfiguration(
                connectionInformation,
                errorStrategy.orElseGet(FeatureClientErrorStrategy::nullValueStrategy),
                cacheConfiguration.orElseGet(() -> FeatureCacheConfiguration.newBuilder().enabled(false).build()),
                httpClient.orElseGet(IzanamiHttpClient.DefaultIzanamiHttpClient::new),
                callTimeout.orElse(Duration.ofSeconds(10L)),
                castStrategy.orElse(BooleanCastStrategy.LAX)
        );

        if(this.configuration.cacheConfiguration.useServerSentEvent) {
            LOGGER.info("Izanami client will use SSE to keep in sync");
            var service = new SSEFeatureService(configuration);
            if(Objects.nonNull(idsToPreload) && !idsToPreload.isEmpty()) {
                this.loader = service.featureStates(FeatureRequest.newFeatureRequest().withFeatures(idsToPreload)).thenApply(osef -> null);
            } else {
                this.loader = CompletableFuture.completedFuture(null);
            }
            this.featureService = service;
        } else {
            if(configuration.cacheConfiguration.enabled) {
                LOGGER.info("Izanami client will use polling to keep in sync");
            } else {
                LOGGER.info("Cache is disabled, Izanami client will query remote instance every time");
            }
            this.featureService = new FetchFeatureService(configuration);
            if(Objects.nonNull(idsToPreload) && !idsToPreload.isEmpty()) {
                this.loader = featureService.featureStates(FeatureRequest.newFeatureRequest().withFeatures(idsToPreload)).thenAccept(v -> {});
            } else {
                this.loader = CompletableFuture.completedFuture(null);
            }
        }
    }

    /**
     * Close underlying SSE client if SSE client is used, otherwise do nothing.
     * @return a CompletableFuture that complete when SSE client is closed, or immediately if there is no SSE client
     */
    public CompletableFuture<Void> close() {
        if(this.featureService instanceof SSEFeatureService) {
            return ((SSEFeatureService)this.featureService).disconnect();
        } else {
            return CompletableFuture.completedFuture(null);
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
     * @deprecated use checkFeatureValues instead
     */
    @Deprecated
    public CompletableFuture<Map<String, Boolean>> checkFeatureActivations(FeatureRequest request) {
        return featureService.featureStates(request);
    }

    /**
     * Retrieve activation status for a single feature
     * @param request containing feature id to query and optionally more information about query, cache use or error strategy.
     * @return a CompletableFuture containing activation status for requested feature
     * @deprecated use checkBooleanFeatureValue instead
     */
    @Deprecated
    public CompletableFuture<Boolean> checkFeatureActivation(SingleFeatureRequest request) {
        return featureService.featureStates(request);
    }

    /**
     * Retrieve string value of the flag given request
     * @param request request to match
     * @return a CompletableFuture that will resolve with requested feature value.
     * If feature does not have a string value error strategy will be used to determine value to return.
     */
    public CompletableFuture<String> stringValue(SingleFeatureRequest request) {
        return featureService.stringFeatureValue(request);
    }

    /**
     * Retrieve number value of the flag given request
     * @param request request to match
     * @return a CompletableFuture that will resolve with requested feature value.
     * If feature does not have a number value error strategy will be used to determine value to return.
     */
    public CompletableFuture<BigDecimal> numberValue(SingleFeatureRequest request) {
        return featureService.numberFeatureValue(request);
    }

    /**
     * Retrieve boolean value of the flag given request
     * @param request request to match
     * @return a CompletableFuture that will resolve with requested feature value. If feature does not have a boolean value:
     * <ul>
     *     <li>if a LAX BooleanCastStrategy was specified, value will be casted to boolean (empty string, numeric 0 and null are false, everything else is true).</li>
     *     <li>if STRICT BooleanCastStrategy was specified (or if no strategy was specified) error strategy will be used to determine value to return.</li>
     * </ul>
     */
    public CompletableFuture<Boolean> booleanValue(SingleFeatureRequest request) {
        return featureService.booleanFeatureValue(request);
    }

    /**
     * Return multiple feature values.
     * @param request feature request
     * @return a completable future containing feature values
     */
    public CompletableFuture<IzanamiResult> featureValues(FeatureRequest request) {
        return featureService.featureValues(request);
    }

    /**
     * Indicate when client is loaded. A loaded client has fetch ids to preload (if provided). If no ids were provided, client is ready immediately after its instantiation.
     * @return a CompletableFuture that resolve when client has loaded id to preload (if any).
     */
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
        private Optional<BooleanCastStrategy> castStrategy = Optional.empty();

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
         * Default strategy to use to cast non-boolean values in boolean when needed.
         * Possible values are STRICT (trying to cast non-boolean value to boolean value will fail) and LAX (empty string, numeric 0 and null are false, everything else is true).
         * This strategy can be overridden both at query and query feature levels.
         * @param strategy boolean cast strategy to use
         * @return updated builder
         */
        public IzanamiClientBuilder withBooleanCastStrategy(BooleanCastStrategy strategy) {
            this.castStrategy = Optional.ofNullable(strategy);
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
                    idsToPreload,
                    castStrategy
            );
        }
    }
}