package is.hello.speech.core.models;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import is.hello.speech.core.handlers.results.AlarmResult;

import java.util.Collections;
import java.util.Map;

/**
 * Created by ksg on 7/25/16
 */
public class HandlerResult {
    public static final String EMPTY_COMMAND = "NONE";
    private static final String TEXT_RESPONSE_FIELD = "text";

    public final HandlerType handlerType;
    public final String command;
    public Map<String, String> responseParameters;

    // all the handler results
    public final Optional<AlarmResult> alarmResult;


    public HandlerResult(final HandlerType handlerType, final String command, final Map<String, String> responseParameters,
                         final Optional<AlarmResult> alarmResult) {
        this.handlerType = handlerType;
        this.command = command;
        this.responseParameters = responseParameters;
        this.alarmResult = alarmResult;
    }

    public String getResponseText() {
        if (responseParameters.containsKey(TEXT_RESPONSE_FIELD)) {
            return responseParameters.get(TEXT_RESPONSE_FIELD);
        } else {
            return "";
        }
    }

    public static HandlerResult emptyResult() {
        return new HandlerResult(HandlerType.NONE, EMPTY_COMMAND, Maps.newHashMap(), Optional.absent());
    }

    public static class Builder {
        private HandlerType handlerType = HandlerType.NONE;
        private String command = EMPTY_COMMAND;
        private Map<String, String> parameters = Collections.emptyMap();
        private Optional<AlarmResult> alarmResult = Optional.absent();

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

        public Builder withAlarmResult(final AlarmResult result) {
            this.alarmResult = Optional.of(result);
            return this;
        }

        public HandlerResult build() {
            return new HandlerResult(handlerType, command, parameters, alarmResult);
        }
    }
}
