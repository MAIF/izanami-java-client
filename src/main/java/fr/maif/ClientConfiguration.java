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
    }
}
