package fr.maif;

import java.time.Duration;

/**
 * This class allows to configure cache behaviour for izanami client.
 */
public class FeatureCacheConfiguration {
    /**
     * Indicate whether cache should be used (when possible) when querying feature activation status.
     */
    public final boolean enabled;
    /**
     * Cache refresh interval, the cache will refresh all stored features at this interval
     */
    public final Duration refreshInterval;

    private FeatureCacheConfiguration(Builder builder) {
        enabled = builder.enabled;
        refreshInterval = builder.refreshInterval;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean enabled = false;
        private Duration refreshInterval = Duration.ofMinutes(10L);

        private Builder() {
        }

        /**
         * @param val whether cache should be used (when possible) when querying feature activation status.
         * @return updated builder
         */
        public Builder enabled(boolean val) {
            enabled = val;
            return this;
        }

        /**
         * @param val Cache refresh interval, the cache will refresh all stored features at this interval
         * @return updated builder
         */
        public Builder withRefreshInterval(Duration val) {
            refreshInterval = val;
            return this;
        }

        /**
         * Build actual cache configuration
         * @return a new FeatureCacheConfiguration with this builder values
         */
        public FeatureCacheConfiguration build() {
            return new FeatureCacheConfiguration(this);
        }
    }
}
