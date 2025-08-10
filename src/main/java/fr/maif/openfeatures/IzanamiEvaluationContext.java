package fr.maif.openfeatures;

import java.util.Optional;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.Value;

public class IzanamiEvaluationContext {
    public static String IZANAMI_CONTEXT = "context";
    public final String user;
    public final String context;

    private IzanamiEvaluationContext(String user, String context) {
        this.user = user;
        this.context = context;
    }

    public static IzanamiEvaluationContext fromContext(EvaluationContext evaluationContext) {
        String user = evaluationContext.getTargetingKey();
        String context = Optional.ofNullable(evaluationContext.getValue(IZANAMI_CONTEXT)).map(Value::asString).orElse(null);

        return new IzanamiEvaluationContext(user, context);

    }

    public static IzanamiEvaluationContextBuilder newBuilder() {
        return new IzanamiEvaluationContextBuilder();
    }

    public static IzanamiEvaluationContextBuilder toBuilder(IzanamiEvaluationContext builder) {
        return new IzanamiEvaluationContextBuilder(builder.user, builder.context);
    }

    public static class IzanamiEvaluationContextBuilder {
        private String user;
        private String context;

        public IzanamiEvaluationContextBuilder() {}

        public IzanamiEvaluationContextBuilder(String user, String context) {
            this.user = user;
            this.context = context;
        }

        public IzanamiEvaluationContextBuilder withUser(String user) {
            this.user = user;
            return this;
        }

        public IzanamiEvaluationContextBuilder withContext(String context) {
            this.context = context;
            return this;
        }

        public IzanamiEvaluationContext build() {
            return new IzanamiEvaluationContext(user, context);
        }
    }
}
