package fr.maif;

import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.http.IzanamiHttpClient;
import fr.maif.requests.IzanamiConnectionInformation;

import java.time.Duration;


public class ClientConfiguration {
    public final IzanamiConnectionInformation connectionInformation;
    public final FeatureClientErrorStrategy errorStrategy;
    public final FeatureCacheConfiguration cacheConfiguration;
    public final IzanamiHttpClient httpClient;
    public final Duration callTimeout;
    public final BooleanCastStrategy castStrategy;
    public final int maxUrlSize;

    public ClientConfiguration(
            IzanamiConnectionInformation connectionInformation,
            FeatureClientErrorStrategy errorStrategy,
            FeatureCacheConfiguration cacheConfiguration,
            IzanamiHttpClient httpClient,
            Duration callTimeout,
            BooleanCastStrategy castStrategy,
            int maxUrlSize
    ) {
        this.connectionInformation = connectionInformation;
        this.errorStrategy = errorStrategy;
        this.cacheConfiguration = cacheConfiguration;
        this.httpClient = httpClient;
        this.callTimeout = callTimeout;
        this.castStrategy = castStrategy;
        this.maxUrlSize = maxUrlSize;
    }

    @Deprecated
    public ClientConfiguration(
            IzanamiConnectionInformation connectionInformation,
            FeatureClientErrorStrategy errorStrategy,
            FeatureCacheConfiguration cacheConfiguration,
            IzanamiHttpClient httpClient,
            Duration callTimeout,
            BooleanCastStrategy castStrategy
    ) {
        this.connectionInformation = connectionInformation;
        this.errorStrategy = errorStrategy;
        this.cacheConfiguration = cacheConfiguration;
        this.httpClient = httpClient;
        this.callTimeout = callTimeout;
        this.castStrategy = castStrategy;
        this.maxUrlSize = 1_800;
    }

    @Deprecated
    public ClientConfiguration(
            IzanamiConnectionInformation connectionInformation,
            FeatureClientErrorStrategy errorStrategy,
            FeatureCacheConfiguration cacheConfiguration,
            IzanamiHttpClient httpClient,
            Duration callTimeout
    ) {
        this.connectionInformation = connectionInformation;
        this.errorStrategy = errorStrategy;
        this.cacheConfiguration = cacheConfiguration;
        this.httpClient = httpClient;
        this.callTimeout = callTimeout;
        this.castStrategy = BooleanCastStrategy.LAX;
        this.maxUrlSize = 1_800;
    }
}
