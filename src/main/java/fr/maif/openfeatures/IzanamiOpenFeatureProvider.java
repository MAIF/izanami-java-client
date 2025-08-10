package fr.maif.openfeatures;

import static dev.openfeature.sdk.Structure.mapToStructure;
import static fr.maif.FeatureClientErrorStrategy.defaultValueStrategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import fr.maif.IzanamiClient;
import fr.maif.requests.SingleFeatureRequest;

public class IzanamiOpenFeatureProvider implements FeatureProvider {
    private final IzanamiClient izanamiClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public IzanamiOpenFeatureProvider(IzanamiClient izanamiClient) {
        this.izanamiClient = izanamiClient;
    }

    @Override
    public Metadata getMetadata() {
        return () -> "Izanami provider";
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {}

    @Override
    public void shutdown() {
        izanamiClient.close().join();
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        var izanamiContext = IzanamiEvaluationContext.fromContext(ctx);
        var result = izanamiClient.booleanValue(SingleFeatureRequest.newSingleFeatureRequest(key)
                .withContext(izanamiContext.context)
                .withUser(izanamiContext.user)
                .withErrorStrategy(defaultValueStrategy(defaultValue, null, null))
        ).join();
        return ProviderEvaluation.<Boolean>builder().value(result).build();
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        var izanamiContext = IzanamiEvaluationContext.fromContext(ctx);
        var result = izanamiClient.stringValue(SingleFeatureRequest.newSingleFeatureRequest(key)
                .withContext(izanamiContext.context)
                .withUser(izanamiContext.user)
                .withErrorStrategy(defaultValueStrategy(false, defaultValue, null))
            ).join();
        return ProviderEvaluation.<String>builder().value(result).build();
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        var izanamiContext = IzanamiEvaluationContext.fromContext(ctx);
        var result = izanamiClient.numberValue(SingleFeatureRequest.newSingleFeatureRequest(key)
                .withContext(izanamiContext.context)
                .withUser(izanamiContext.user)
                .withErrorStrategy(defaultValueStrategy(false, null, BigDecimal.valueOf(defaultValue)))
        ).join();
        return ProviderEvaluation.<Integer>builder().value(result.intValue()).build();
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        var izanamiContext = IzanamiEvaluationContext.fromContext(ctx);
        var result = izanamiClient.numberValue(SingleFeatureRequest.newSingleFeatureRequest(key)
                .withContext(izanamiContext.context)
                .withUser(izanamiContext.user)
                .withErrorStrategy(defaultValueStrategy(false, null, BigDecimal.valueOf(defaultValue)))
        ).join();
        return ProviderEvaluation.<Double>builder().value(result.doubleValue()).build();
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        var izanamiContext = IzanamiEvaluationContext.fromContext(ctx);
        var result = izanamiClient.stringValue(SingleFeatureRequest.newSingleFeatureRequest(key)
                .withContext(izanamiContext.context)
                .withUser(izanamiContext.user)
                .withErrorStrategy(
                        defaultValueStrategy(
                                defaultValue.asBoolean(),
                                defaultValue.asString(),
                                Optional.ofNullable(defaultValue.asDouble()).map(BigDecimal::valueOf)
                                        .or(() -> Optional.ofNullable(defaultValue.asInteger()).map(BigDecimal::valueOf))
                                        .orElse(null)
                        )
                )
        ).join();
        try {
            Object tree = mapper.readValue(result, Object.class);
            return ProviderEvaluation.<Value>builder().value(objectToValue(tree)).build();
        } catch (JsonProcessingException e) {
            throw new TypeMismatchError("Flag " + key + " evaluation returned invalid json.");
        }
    }


    private Value objectToValue(Object object) {
        if (object instanceof Value) {
            return (Value) object;
        } else if (object == null) {
            return null;
        } else if (object instanceof String) {
            return new Value((String) object);
        } else if (object instanceof Boolean) {
            return new Value((Boolean) object);
        } else if (object instanceof Integer) {
            return new Value((Integer) object);
        } else if (object instanceof Double) {
            return new Value((Double) object);
        } else if (object instanceof Structure) {
            return new Value((Structure) object);
        } else if (object instanceof List) {
            // need to translate each elem in list to a value
            return new Value(
                    ((List<Object>) object).stream().map(this::objectToValue).collect(Collectors.toList()));
        } else if (object instanceof Instant) {
            return new Value((Instant) object);
        } else if (object instanceof Map) {
            return new Value(mapToStructure((Map<String, Object>) object));
        } else if (object instanceof ObjectNode) {
            ObjectNode objectNode = (ObjectNode) object;
            return objectToValue(new ObjectMapper().convertValue(objectNode, Object.class));
        } else {
            throw new TypeMismatchError("Flag value " + object + " had unexpected type " + object.getClass() + ".");
        }
    }


}
