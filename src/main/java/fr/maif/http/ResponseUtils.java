package fr.maif.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.maif.errors.IzanamiException;
import fr.maif.features.*;
import fr.maif.features.ActivationCondition.NumberValuedActivationCondition;
import fr.maif.features.ActivationCondition.StringValuedActivationCondition;
import fr.maif.features.Feature.BooleanFeature;
import fr.maif.features.Feature.NumberFeature;
import fr.maif.features.Feature.StringFeature;
import fr.maif.features.FeatureOverload.ClassicalOverload;
import fr.maif.features.FeatureOverload.NumberOverload;
import fr.maif.features.FeatureOverload.StringOverload;
import fr.maif.features.FeatureOverload.WasmFeatureOverload;
import fr.maif.features.values.BooleanValue;
import fr.maif.features.values.NumberValue;
import fr.maif.features.values.StringValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class ResponseUtils {
    public static final ObjectMapper mapper = new ObjectMapper();
    public static final Logger LOGGER = LoggerFactory.getLogger(ResponseUtils.class);
    static {
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static Result<Map<String, Boolean>> parseBooleanResponse(String json) {
        try {
            return Optional
                    .ofNullable(mapper.readValue(json, new TypeReference<Map<String, IzanamiServerFeatureResponse>>() {
                    }))
                    .map(map -> {
                        Map<String, Boolean> result = new HashMap<>();
                        map.entrySet()
                                .forEach(entry -> {
                                    result.put(entry.getKey(), entry.getValue().active);
                                });
                        return result;
                    })
                    .map(Result::new)
                    .orElseGet(() -> new Result<>("Failed to parse response"));
        } catch (JsonMappingException e) {
            return new Result<>("Unexpected format received from Izanami: " + json);
        } catch (JsonProcessingException e) {
            return new Result<>("Invalid JSON received from Izanami: " + json);
        }
    }

    public static Result<Map<String, Feature>> parseFeatureResponse(String json) {
        try {
            return Optional.ofNullable(mapper.readValue(json, new TypeReference<Map<String, ObjectNode>>() {}))
                    .map(map -> map.entrySet().stream()
                            .map(entry -> ResponseUtils.parseFeature(entry.getKey(), entry.getValue()))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(Collectors.toMap(f -> f.id, f -> f)))
                    .map(Result::new)
                    .orElseGet(() -> new Result<>("Failed to parse response"));
        } catch (JsonMappingException e) {
            return new Result<>("Unexpected format received from Izanami: " + json);
        } catch (JsonProcessingException e) {
            return new Result<>("Invalid JSON received from Izanami: " + json);
        }
    }


    public static Optional<Feature> parseFeature(String id, ObjectNode json) {
        if (json.isNull()) {
            return Optional.empty();
        }

        String name = json.get("name").asText();
        String project = json.get("project").asText();
        JsonNode activeNode = json.get("active");
        String type = "unknown";
        if (activeNode.isNumber()) {
            type = "number";
        } else if (activeNode.isTextual()) {
            type = "string";
        } else if (activeNode.isBoolean()) {
            type = "boolean";
        } else if(activeNode.isNull()) {
            ObjectNode conditions = (ObjectNode) json.get("conditions");
            try {
                // when "active" field is null, we try to deduce feature type from conditions.
                var nodeById = mapper.treeToValue(conditions, new TypeReference<Map<String, ObjectNode>>() {});
                Set<String> types = nodeById.values().stream().map(js -> {
                    if (js.has("value")) {
                        JsonNode value = js.get("value");
                        if (value.isTextual()) {
                            return Optional.of("string");
                        } else if (value.isNumber()) {
                            return Optional.of("number");
                        } else {
                            throw new IzanamiException("Invalid type for active field response from Izanami: " + value.getNodeType());
                        }
                    } else if(!js.has("wasmConfig") || js.get("wasmConfig").isNull()) {
                        return Optional.of("boolean");
                    } else {
                        return Optional.<String>empty();
                    }
                }).flatMap(Optional::stream)
                .collect(Collectors.toSet());

                if(types.size() > 1) {
                    throw new IzanamiException("Multiple types detected in feature conditions: " + json);
                } else if(types.size() == 1) {
                    type = types.iterator().next();
                }

            } catch (JsonProcessingException e) {
                throw new IzanamiException(e);
            }
        }

        switch (type) {
            case "number": {
                BigDecimal active = json.get("active").isNull() ? null : json.get("active").decimalValue();
                ObjectNode conditions = (ObjectNode) json.get("conditions");
                Map<String, FeatureOverload<NumberValue>> overloads = new HashMap<>();
                try {
                    var nodeById = mapper.treeToValue(conditions, new TypeReference<Map<String, ObjectNode>>() {
                    });
                    overloads = nodeById.entrySet().stream()
                            .map(entry -> {
                                var overloadJson = entry.getValue();
                                return parseNumberFeatureOverload(overloadJson)
                                        .map(overload -> new AbstractMap.SimpleEntry<>(entry.getKey(), overload));
                            }).filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                } catch (JsonProcessingException e) {
                    throw new IzanamiException(e);
                }

                return Optional.of(new NumberFeature(
                        id,
                        name,
                        project,
                        active,
                        overloads)
                );
            }
            case "string": {
                String active = json.get("active").isNull() ? null : json.get("active").asText();
                ObjectNode conditions = (ObjectNode) json.get("conditions");
                Map<String, FeatureOverload<StringValue>> overloads = new HashMap<>();
                try {
                    var nodeById = mapper.treeToValue(conditions, new TypeReference<Map<String, ObjectNode>>() {
                    });
                    overloads = nodeById.entrySet().stream()
                            .map(entry -> {
                                var overloadJson = entry.getValue();
                                return parseStringFeatureOverload(overloadJson)
                                        .map(overload -> new AbstractMap.SimpleEntry<>(entry.getKey(), overload));
                            }).filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                } catch (JsonProcessingException e) {
                    throw new IzanamiException(e);
                }

                return Optional.of(new StringFeature(
                        id,
                        name,
                        project,
                        active,
                        overloads)
                );
            }
            case "boolean": {
                boolean active = json.get("active").asBoolean();
                ObjectNode conditions = (ObjectNode) json.get("conditions");
                Map<String, FeatureOverload<BooleanValue>> overloads = new HashMap<>();
                try {
                    var nodeById = mapper.treeToValue(conditions, new TypeReference<Map<String, ObjectNode>>() {
                    });
                    overloads = nodeById.entrySet().stream()
                            .map(entry -> {
                                var overloadJson = entry.getValue();
                                return parseFeatureOverload(overloadJson)
                                        .map(overload -> new AbstractMap.SimpleEntry<>(entry.getKey(), overload));
                            }).filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                } catch (JsonProcessingException e) {
                    throw new IzanamiException(e);
                }

                return Optional.of(new BooleanFeature(
                        id,
                        name,
                        project,
                        active,
                        overloads)
                );
            }
            default:
                throw new IzanamiException(
                        "Invalid type for active field response from Izanami: " + activeNode.getNodeType());
        }
    }

    static Optional<FeatureOverload<BooleanValue>> parseFeatureOverload(ObjectNode node) {
        boolean enabled = node.get("enabled").asBoolean();
        if (node.has("conditions") && !(node.get("conditions") instanceof NullNode)) {
            List<ActivationCondition> conditions = StreamSupport
                    .stream(node.get("conditions").spliterator(), false)
                    .map(ResponseUtils::parseActivationCondition)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            return Optional.of(new ClassicalOverload(enabled, conditions));
        } else if (node.has("wasmConfig") && !node.get("wasmConfig").isNull()) {
            String name = node.get("wasmConfig").get("name").asText();

            return Optional.of(new WasmFeatureOverload<BooleanValue>(enabled, new FeatureOverload.WasmConfig(name)));
        } else {
            LOGGER.error("Failed to parse feature overload " + node);
            return Optional.empty();
        }

    }

    static Optional<FeatureOverload<StringValue>> parseStringFeatureOverload(ObjectNode node) {
        boolean enabled = node.get("enabled").asBoolean();
        if (node.has("conditions") && !(node.get("conditions") instanceof NullNode) && node.get("value").isTextual()) {
            List<StringValuedActivationCondition> conditions = StreamSupport
                    .stream(node.get("conditions").spliterator(), false)
                    .map(ResponseUtils::parseStringActivationCondition)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            return Optional.of(new StringOverload(enabled, conditions, node.get("value").asText()));
        } else if (node.has("wasmConfig") && !node.get("wasConfig").isNull()) {
            String name = node.get("wasmConfig").get("name").asText();

            return Optional.of(new WasmFeatureOverload<StringValue>(enabled, new FeatureOverload.WasmConfig(name)));
        } else {
            LOGGER.error("Failed to parse feature overload " + node);
            return Optional.empty();
        }
    }

    static Optional<FeatureOverload<NumberValue>> parseNumberFeatureOverload(ObjectNode node) {
        boolean enabled = node.get("enabled").asBoolean();
        if (node.has("conditions") && !(node.get("conditions") instanceof NullNode) && node.get("value").isNumber()) {
            List<NumberValuedActivationCondition> conditions = StreamSupport
                    .stream(node.get("conditions").spliterator(), false)
                    .map(ResponseUtils::parseNumberActivationCondition)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            return Optional.of(new NumberOverload(enabled, conditions, node.get("value").decimalValue()));
        } else if (node.has("wasmConfig") && !node.get("wasmConfig").isNull()) {
            String name = node.get("wasmConfig").get("name").asText();

            return Optional.of(new WasmFeatureOverload<NumberValue>(enabled, new FeatureOverload.WasmConfig(name)));
        } else {
            LOGGER.error("Failed to parse feature overload " + node);
            return Optional.empty();
        }
    }

    static Optional<ActivationCondition> parseActivationCondition(JsonNode json) {
        if (json.isNull()) {
            return Optional.empty();
        }

        var maybePeriod = Optional.ofNullable(json.get("period")).flatMap(ResponseUtils::parseFeaturePeriod);
        var maybeRule = Optional.ofNullable(json.get("rule")).flatMap(ResponseUtils::parseRule);

        return Optional.of(new ActivationCondition(maybePeriod.orElse(null), maybeRule.orElse(null)));
    }

    static Optional<StringValuedActivationCondition> parseStringActivationCondition(JsonNode json) {
        JsonNode valueNode = json.get("value");

        if (!valueNode.isTextual()) {
            return Optional.empty();
        }

        return parseActivationCondition(json)
                .map(cond -> StringValuedActivationCondition.fromCondition(cond, valueNode.asText()));

    }

    static Optional<NumberValuedActivationCondition> parseNumberActivationCondition(JsonNode json) {
        JsonNode valueNode = json.get("value");

        if (!valueNode.isNumber()) {
            return Optional.empty();
        }

        return parseActivationCondition(json)
                .map(cond -> NumberValuedActivationCondition.fromCondition(cond, valueNode.decimalValue()));

    }

    static Optional<FeaturePeriod> parseFeaturePeriod(JsonNode node) {
        if (node.isNull()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(mapper.readValue(node.toPrettyString(), FeaturePeriod.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    static Optional<? extends ActivationRule> parseRule(JsonNode node) {
        if (node.isNull()) {
            return Optional.empty();
        }
        if (node.has("users")) {
            return parseUserList(node);
        } else if (node.has("percentage")) {
            return parseUserPercentage(node);
        } else {
            return Optional.empty();
        }
    }

    static Optional<UserList> parseUserList(JsonNode node) {
        if (node.isNull()) {
            return Optional.empty();
        }

        Set<String> users = StreamSupport.stream(node.get("users").spliterator(), false)
                .map(JsonNode::asText).collect(Collectors.toSet());

        return Optional.of(new UserList(users));
    }

    static Optional<UserPercentage> parseUserPercentage(JsonNode node) {
        if (node.isNull()) {
            return Optional.empty();
        }

        var percentage = node.get("percentage").asInt();

        return Optional.of(new UserPercentage(percentage));
    }

    static class IzanamiServerFeatureResponse {
        public Boolean active;
        public String name;
    }
}
