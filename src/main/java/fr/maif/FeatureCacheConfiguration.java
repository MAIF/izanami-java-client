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

    /**
     * Wether izanami client will received feature updates from remote server via SSE.
     */
    public final boolean useServerSentEvent;

    /**
     * Maximum time between two Izanami heartbeats on SSE connection. Used only when {@link FeatureCacheConfiguration#useServerSentEvent} is true.
     */
    public final Duration serverSentEventKeepAliveInterval;

    private FeatureCacheConfiguration(Builder builder) {
        enabled = builder.enabled;
        useServerSentEvent = builder.useServerSentEvent;
        refreshInterval = builder.refreshInterval;
        serverSentEventKeepAliveInterval = builder.serverSentEventKeepAliveInterval;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean enabled = false;
        private Duration refreshInterval = Duration.ofMinutes(10L);
        private boolean useServerSentEvent = false;
        public Duration serverSentEventKeepAliveInterval = Duration.ofSeconds(25L);

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
         * @param val wether client should use SSE instead of polling to keep cache up to date. When using SSE,
         *            Izanami client will keep an http connection opened with Izanami backend, and get notified as soon
         *            as a feature is created / updated / deleted.
         * @return updated builder
         */
        public Builder shouldUseServerSentEvent(boolean val) {
            this.useServerSentEvent = val;
            return this;
        }

        /**
         * @param val wether client should use SSE instead of polling to keep cache up to date. When using SSE,
         *            Izanami client will keep an http connection opened with Izanami backend, and get notified as soon
         *            as a feature is created / updated / deleted.
         * @return updated builder
         */
        public Builder withServerSentEventKeepAliveInterval(Duration val) {
            this.serverSentEventKeepAliveInterval = val;
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
