package fr.maif.requests;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import fr.maif.features.results.IzanamiResult;
import fr.maif.features.values.FeatureValue;

public interface FeatureService {
    CompletableFuture<Map<String, Boolean>> featureStates(
            FeatureRequest request
    );

    default CompletableFuture<Boolean> featureStates(SingleFeatureRequest request) {
        return featureStates(request.toActivationRequest())
                .thenApply(resp -> resp.get(request.feature));
    }

    CompletableFuture<IzanamiResult> featureValues(
            FeatureRequest request
    );

    default CompletableFuture<FeatureValue> featureValues(SingleFeatureRequest request) {
        return featureValues(request.toActivationRequest())
                .thenApply(resp -> resp.get(request.feature));
    }
}
