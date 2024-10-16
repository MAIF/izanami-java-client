package fr.maif.requests;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import fr.maif.ClientConfiguration;
import fr.maif.features.results.IzanamiResult;

public interface FeatureService {
    ClientConfiguration configuration();

    @Deprecated
    default CompletableFuture<Map<String, Boolean>> featureStates(
            FeatureRequest request
    ) {
        return featureValues(request)
            .thenApply(result -> {
                Map<String, Boolean> states = new HashMap<>();
                result.results.forEach((key, value) -> states.put(key, value.booleanValue(request.castStrategy.orElse(configuration().castStrategy))));
                return states;
            });
    }

    @Deprecated
    default CompletableFuture<Boolean> featureStates(SingleFeatureRequest request) {
        return featureStates(request.toActivationRequest())
                .thenApply(resp -> resp.get(request.feature));
    }

    CompletableFuture<IzanamiResult> featureValues(
            FeatureRequest request
    );

    default CompletableFuture<String> stringFeatureValue(SingleFeatureRequest request) {
        return featureValues(request.toActivationRequest())
                .thenApply(value -> value.stringValue(request.feature));
    }

    default CompletableFuture<BigDecimal> numberFeatureValue(SingleFeatureRequest request) {
        return featureValues(request.toActivationRequest())
                .thenApply(value -> value.numberValue(request.feature));
    }

    default CompletableFuture<Boolean> booleanFeatureValue(SingleFeatureRequest request) {
        return featureStates(request);
    }
}
