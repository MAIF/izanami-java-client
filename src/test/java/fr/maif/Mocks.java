package fr.maif;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.maif.http.ResponseUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

public class Mocks {
    private static final Random generator = new Random();
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    public static MockedFeature feature(String name, boolean active) {
        var f = new MockedFeature();
        f.name = name;
        f.active = active;
        return f;
    }

    public static MockOverload overload(boolean enabled) {
        var o = new MockOverload();
        o.enabled = enabled;
        return o;

    }

    public static MockCondition condition(boolean enabled) {
        return new MockCondition();
    }

    public static MockPeriod period() {
        return new MockPeriod();
    }

    public static MockPercentageRule percentageRule(int percentage) {
        var p = new MockPercentageRule();
        p.percentage = percentage;
        return p;
    }

    public static MockUserListRule userListRule(String... users) {
        var us = Arrays.stream(users).collect(Collectors.toList());
        var u = new MockUserListRule();
        u.users = us;
        return u;
    }

    public static MockActivationDays days(String... days) {
        var d = new MockActivationDays();
        d.days = Arrays.stream(days).collect(Collectors.toList());
        return d;
    }

    public static MockedIzanamiResponse newResponse() {
        return new MockedIzanamiResponse();
    }


    public static class MockedIzanamiResponse {
        Map<String, MockedFeature> features = new HashMap<>();

        public MockedIzanamiResponse withFeature(String id, MockedFeature feature) {
            this.features.put(id, feature);
            return this;
        }

        public String toJson() {
            try {
                return mapper.writeValueAsString(features);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        public String toSSEJson() {
            var enveloppe = eventEnveloppe("FEATURE_STATES", Optional.empty());
            try {
                enveloppe.put("payload", ResponseUtils.mapper.readTree(toJson()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            try {
                return ResponseUtils.mapper.writeValueAsString(enveloppe);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static ObjectNode eventEnveloppe(String type, Optional<LocalDateTime> timestamp) {
        try {
            var map = Map.of("_id", generator.nextLong(),
                    "timestamp", ResponseUtils.mapper.writeValueAsString(timestamp.map(ts -> ts.toInstant(ZoneOffset.UTC).toEpochMilli()).orElseGet(() -> Instant.now().toEpochMilli())),
                    "type", type);
            return ResponseUtils.mapper.convertValue(map, ObjectNode.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static class MockedFeature {
        public String name;
        public boolean active;
        public String project = "default";
        public Map<String, MockOverload> conditions = new HashMap<>();

        public MockedFeature active(boolean active) {
            this.active = active;
            return this;
        }
        public MockedFeature withOverload(MockOverload condition) {
            this.conditions.put("", condition);
            return this;
        }

        public MockedFeature withOverload(String context, MockOverload condition) {
            this.conditions.put(context, condition);
            return this;
        }

        public String toJson() {
            try {
                return mapper.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        public String toUpdatedEvent(String id) {
            var enveloppe = eventEnveloppe("FEATURE_UPDATED", Optional.empty());
            enveloppe.put("payload", mapper.convertValue(this, ObjectNode.class));
            enveloppe.put("id", new TextNode(id));
            try {
                return mapper.writeValueAsString(enveloppe);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        public String toAddedEvent(String id) {
            var enveloppe = eventEnveloppe("FEATURE_CREATED", Optional.empty());
            enveloppe.put("payload", mapper.convertValue(this, ObjectNode.class));
            enveloppe.put("id", new TextNode(id));
            try {
                return mapper.writeValueAsString(enveloppe);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static String emptyFeatureStatesEvent() {
        var enveloppe = eventEnveloppe("FEATURE_STATES", Optional.empty());
        try {
            enveloppe.put("payload", mapper.convertValue(new HashMap<>(), ObjectNode.class));
            return mapper.writeValueAsString(enveloppe);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String featureDeletedEvent(String id) {
        var enveloppe = eventEnveloppe("FEATURE_DELETED", Optional.empty());
        enveloppe.put("payload", new TextNode(id));

        try {
            return mapper.writeValueAsString(enveloppe);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static class MockOverload {
        public boolean enabled;
        public List<MockCondition> conditions = new ArrayList<>();
        public MockScript wasmConfig = null;

        public MockOverload withCondition(MockCondition condition) {
            this.conditions.add(condition);
            return this;
        }

        public MockOverload withScript(String name) {
            this.wasmConfig = new MockScript(name);
            this.conditions = null;
            return this;
        }


    }

    public static class MockScript {
        public String name;

        public MockScript(String name) {
            this.name = name;
        }
    }

    public static class MockCondition {
        public MockPeriod period;
        public MockRule rule;

        public MockCondition withPeriod(MockPeriod period) {
            this.period = period;
            return this;
        }

        public MockCondition withRule(MockRule rule) {
            this.rule = rule;
            return this;
        }
    }

    public static class MockPeriod {
        public LocalDateTime begin;
        public LocalDateTime end;
        public TimeZone timezone;
        public MockActivationDays activationDays;

        public MockPeriod withBegin(LocalDateTime begin) {
            this.begin = begin;
            return this;
        }
        public MockPeriod withEnd(LocalDateTime end) {
            this.end = end;
            return this;
        }
        public MockPeriod withTimezone(TimeZone timezone) {
            this.timezone = timezone;
            return this;
        }
        public MockPeriod withActivationDays(MockActivationDays activationDays) {
            this.activationDays = activationDays;
            return this;
        }
    }

    public static class MockActivationDays {
        public List<String> days;
    }

    public static abstract class MockRule {
    }

    public static class MockPercentageRule extends MockRule {
        public Integer percentage;
    }

    public static class MockUserListRule extends MockRule {
        public List<String> users;
    }
}
