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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            return Optional.ofNullable(mapper.readValue(json, new TypeReference<Map<String, IzanamiServerFeatureResponse>>() {}))
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
                                .collect(Collectors.toMap(f -> f.id, f -> f))
                    )
                    .map(Result::new)
                    .orElseGet(() -> new Result<>("Failed to parse response"));
        } catch (JsonMappingException e) {
            return new Result<>("Unexpected format received from Izanami: " + json);
        } catch (JsonProcessingException e) {
            return new Result<>("Invalid JSON received from Izanami: " + json);
        }
    }

    static FeatureOverload parseOverload(ObjectNode json) {
        boolean enabled = json.get("enabled").asBoolean();

        if (json.has("conditions") && !(json.get("conditions") instanceof NullNode)) {
            Set<ActivationCondition> conditions = StreamSupport
                    .stream(json.get("conditions").spliterator(), false)
                    .map(ResponseUtils::parseActivationCondition)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());
            return new FeatureOverload.ClassicalOverload(enabled, conditions);
        } else {
            String name = json.get("wasmConfig").get("name").asText();
            return new FeatureOverload.WasmFeatureOverload(enabled, new FeatureOverload.WasmConfig(name));
        }
    }

    public static Optional<Feature> parseFeature(String id, ObjectNode json) {
        if(json.isNull()) {
            return Optional.empty();
        }

        String name = json.get("name").asText();
        String project = json.get("project").asText();
        boolean active = json.get("active").asBoolean();
        ObjectNode conditions = (ObjectNode) json.get("conditions");
        Map<String, FeatureOverload> overloads = new HashMap<>();
        try {
            var nodeById = mapper.treeToValue(conditions, new TypeReference<Map<String, ObjectNode>>(){});
            overloads = nodeById.entrySet().stream()
                    .map(entry -> {
                        var overloadJson = entry.getValue();
                        return parseFeatureOverload(overloadJson).map(overload -> new AbstractMap.SimpleEntry<>(entry.getKey(), overload));
                    }).filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        } catch (JsonProcessingException e) {
            throw new IzanamiException(e);
        }


        return Optional.of(new Feature(
                id,
                name,
                project,
                active,
                overloads
        ));
    }


    static Optional<FeatureOverload> parseFeatureOverload(ObjectNode node) {
        boolean enabled = node.get("enabled").asBoolean();
        if (node.has("conditions") && !(node.get("conditions") instanceof NullNode)) {
            Set<ActivationCondition> conditions = StreamSupport
                    .stream(node.get("conditions").spliterator(), false)
                    .map(ResponseUtils::parseActivationCondition)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());
            return Optional.of(new FeatureOverload.ClassicalOverload(enabled, conditions));
        } else if(node.has("wasmConfig")) {
            String name = node.get("wasmConfig").get("name").asText();

            return Optional.of(new FeatureOverload.WasmFeatureOverload(enabled, new FeatureOverload.WasmConfig(name)));
        } else {
            LOGGER.error("Failed to parse feature overload " + node);
            return Optional.empty();
        }

    }

    static Optional<ActivationCondition> parseActivationCondition(JsonNode json) {
        if(json.isNull()) {
            return Optional.empty();
        }

        var maybePeriod = Optional.ofNullable(json.get("period")).flatMap(ResponseUtils::parseFeaturePeriod);
        var maybeRule = Optional.ofNullable(json.get("rule")).flatMap(ResponseUtils::parseRule);

        return Optional.of(new ActivationCondition(maybePeriod.orElse(null), maybeRule.orElse(null)));
    }

    static Optional<FeaturePeriod> parseFeaturePeriod(JsonNode node) {
        if(node.isNull()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(mapper.readValue(node.toPrettyString(), FeaturePeriod.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    static Optional<? extends ActivationRule> parseRule(JsonNode node) {
        if(node.isNull()) {
            return Optional.empty();
        }
        if(node.has("users")) {
            return parseUserList(node);
        } else if(node.has("percentage")) {
            return parseUserPercentage(node);
        } else {
            return Optional.empty();
        }
    }

    static Optional<UserList> parseUserList(JsonNode node) {
        if(node.isNull()) {
            return Optional.empty();
        }

        Set<String> users = StreamSupport.stream(node.get("users").spliterator(), false)
                .map(JsonNode::asText).collect(Collectors.toSet());

        return Optional.of(new UserList(users));
    }

    static Optional<UserPercentage> parseUserPercentage(JsonNode node) {
        if(node.isNull()) {
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
