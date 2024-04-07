package fr.maif.requests;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface FeatureService {
    CompletableFuture<Map<String, Boolean>> featureStates(
            FeatureRequest request
    );

    default CompletableFuture<Boolean> featureStates(SingleFeatureRequest request) {
        return featureStates(request.toActivationRequest())
                .thenApply(resp -> resp.get(request.feature));
    }
}
