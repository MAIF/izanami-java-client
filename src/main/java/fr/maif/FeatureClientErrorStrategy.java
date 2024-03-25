package fr.maif;

import fr.maif.errors.IzanamiError;
import fr.maif.errors.IzanamiException;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Strategy to use when one feature activation status can't be fetched or computed locally.
 * Izanami handle errors as follows :
 * <ol>
 *     <li>If feature can't be retrieved from server, the client will use last cached version of the feature (event if cache is disabled) to compute activation status. This computation is possible only for non script features.</li>
 *     <li>If the client is configured to not use last cached version, error strategy will be used</li>
 * </ol>
 *
 * Therefore, error strategy are used in 3 scenarios :
 * <ul>
 *     <li>When feature can't be fetched from Izanami and has never been fetched before</li>
 *     <li>When feature can't be fetched and the client is not allowed to use last feature status to compute it's activation</li>
 *     <li>When feature can't be fetched and it's a script feature</li>
 * </ul>
 *
 * Error strategy can be specified at client level, query level and query feature levels.
 * Priority is : query feature strategy over query strategy over client strategy
 */
public abstract class FeatureClientErrorStrategy<T extends FeatureClientErrorStrategy<T>> {
    public boolean lastKnownFallbackAllowed = true;

    public abstract CompletableFuture<Boolean> handleError(IzanamiError error);

    /**
     *
     * @param shouldUseLastKnownStrategy Indicate whether client is allowed to use last fetched feature state to compute activation status locally if feature retrieval goes wrong. Default is true.
     * @return updated strategy
     */
    public T fallbackOnLastKnownStrategy(boolean shouldUseLastKnownStrategy) {
        this.lastKnownFallbackAllowed = shouldUseLastKnownStrategy;
        return (T) this;
    }


    public static NullValueStrategy nullValueStrategy() {
        return new NullValueStrategy();
    }

    /**
     *
     * @return an error strategy that either throws an IzanamiException (if query was a single feature query) or values feature activation status to null (if query was a multiple feature query).
     */
    public static FailStrategy failStrategy() {
        return new FailStrategy();
    }

    /**
     *
     * @param defaultValue default value to use as activation status
     * @return an error strategy that use provided value as activation status
     */
    public static DefaultValueStrategy defaultValueStrategy(boolean defaultValue) {
        return new DefaultValueStrategy(defaultValue);
    }

    /**
     *
     * @param callback function that will be called to compute feature activation status
     * @return an error strategy that calls provided callback to compute activation status
     */
    public static CallbackStrategy callbackStrategy(
            Function<IzanamiError, CompletableFuture<Boolean>>  callback
    ) {
        return new CallbackStrategy(callback);
    }

    public static class NullValueStrategy extends FeatureClientErrorStrategy<NullValueStrategy> {
        @Override
        public CompletableFuture<Boolean> handleError(IzanamiError error) {
            return CompletableFuture.completedFuture(null);
        }
    }

    public static class FailStrategy extends FeatureClientErrorStrategy<FailStrategy> {
        @Override
        public CompletableFuture<Boolean> handleError(IzanamiError error) {
            return CompletableFuture.failedFuture(new IzanamiException(error.message));
        }
    }

    public static class DefaultValueStrategy extends FeatureClientErrorStrategy<DefaultValueStrategy> {
        public final boolean value;

        public DefaultValueStrategy(boolean value) {
            this.value = value;
        }

        @Override
        public CompletableFuture<Boolean> handleError(IzanamiError error) {
            return CompletableFuture.completedFuture(value);
        }
    }

    public static class CallbackStrategy extends FeatureClientErrorStrategy<CallbackStrategy> {
        private Function<IzanamiError, CompletableFuture<Boolean>> callback;
        public CallbackStrategy(Function<IzanamiError, CompletableFuture<Boolean>> callback) {
            this.callback = callback;
        }

        @Override
        public CompletableFuture<Boolean> handleError(IzanamiError error) {
            return callback.apply(error);
        }
    }

}
