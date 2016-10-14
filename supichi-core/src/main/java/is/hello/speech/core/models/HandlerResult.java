package is.hello.speech.core.models;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import is.hello.speech.core.handlers.results.GenericResult;
import is.hello.speech.core.handlers.results.Outcome;

import java.util.Collections;
import java.util.Map;

/**
 * Created by ksg on 7/25/16
 */
public class HandlerResult {
    public static final String EMPTY_COMMAND = "NONE";
    private static final String TEXT_RESPONSE_FIELD = "text";
    private static final String EMPTY_STRING = "";

    public final HandlerType handlerType;
    public final String command;
    public Map<String, String> responseParameters;

    // all the handler results
    public final Optional<GenericResult> optionalResult;


    public HandlerResult(final HandlerType handlerType, final String command, final Map<String, String> responseParameters,
                         final Optional<GenericResult> optionalResult) {
        this.handlerType = handlerType;
        this.command = command;
        this.responseParameters = responseParameters;
        this.optionalResult = optionalResult;
    }

    public String getResponseText() {
        if (optionalResult.isPresent()) {
            return optionalResult.get().responseText();
        }

        if (responseParameters.containsKey(TEXT_RESPONSE_FIELD)) {
            return responseParameters.get(TEXT_RESPONSE_FIELD);
        }

        return EMPTY_STRING;
    }

    public Outcome getOutcome() {
        if (optionalResult.isPresent()) {
            return optionalResult.get().outcome;
        }

        if (this.responseParameters.containsKey("result")) {
            final String outcomeString = this.responseParameters.get("result");
            return Outcome.fromString(outcomeString);
        }
        return Outcome.FAIL;
    }

    public Optional<String> getErrorText() {
        if (optionalResult.isPresent() && optionalResult.get().errorText.isPresent()) {
            return optionalResult.get().errorText;
        }

        return Optional.absent();
    }

    public static HandlerResult emptyResult() {
        return new HandlerResult(HandlerType.NONE, EMPTY_COMMAND, Maps.newHashMap(), Optional.absent());
    }

    public static class Builder {
        private HandlerType handlerType = HandlerType.NONE;
        private String command = EMPTY_COMMAND;
        private Map<String, String> parameters = Collections.emptyMap();
        private Optional<GenericResult> alarmResult = Optional.absent();

        public Builder withHandlerType(final HandlerType handlerType) {
            this.handlerType = handlerType;
            return this;
        }

        public Builder withCommand(final String command) {
            this.command = command;
            return this;
        }

        public Builder withResponseParameters(final Map<String, String> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder withAlarmResult(final GenericResult result) {
            this.alarmResult = Optional.of(result);
            return this;
        }

        public HandlerResult build() {
            return new HandlerResult(handlerType, command, parameters, alarmResult);
        }
    }
}
