package is.hello.speech.core.models;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import is.hello.speech.core.handlers.results.GenericResult;
import is.hello.speech.core.handlers.results.Outcome;
import is.hello.speech.core.handlers.results.RoomConditionResult;

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

    // TODO: remove this
    public Map<String, String> responseParameters;

    // all the handler results
    public final Optional<GenericResult> optionalResult;
    public final Optional <RoomConditionResult> optionalRoomResult;


    public HandlerResult(final HandlerType handlerType, final String command, final Map<String, String> responseParameters,
                         final Optional<GenericResult> optionalResult, final Optional<RoomConditionResult> optionalRoomResult) {
        this.handlerType = handlerType;
        this.command = command;
        this.responseParameters = responseParameters;
        this.optionalResult = optionalResult;
        this.optionalRoomResult = optionalRoomResult;
    }

    public HandlerResult(final HandlerType handlerType, final String command, final GenericResult result) {
        this.handlerType = handlerType;
        this.command = command;
        this.optionalResult = Optional.of(result);
        this.optionalRoomResult = Optional.absent();
        this.responseParameters = responseParameters(result);
    }

    @Deprecated
    private static Map<String, String> responseParameters(final GenericResult result) {
        final Map<String, String> response = Maps.newHashMap();
        response.put("result", result.outcome.getValue());
        if (result.errorText.isPresent()) {
            response.put("error", result.errorText.get());
            if (result.responseText.isPresent()) {
                response.put("text", result.responseText());
            }
        } else {
            response.put("text", result.responseText());
        }
        return response;
    }

    public Outcome outcome() {
        if (optionalResult.isPresent()) {
            return optionalResult.get().outcome;
        }

        // TODO: deprecated
        if (this.responseParameters.containsKey("result")) {
            final String outcomeString = this.responseParameters.get("result");
            return Outcome.fromString(outcomeString);
        }
        return Outcome.FAIL;
    }


    public String responseText() {
        if (optionalResult.isPresent()) {
            return optionalResult.get().responseText();
        }

        // TODO: deprecated
        if (responseParameters.containsKey(TEXT_RESPONSE_FIELD)) {
            return responseParameters.get(TEXT_RESPONSE_FIELD);
        }

        return EMPTY_STRING;
    }

    public Optional<String> optionalErrorText() {
        if (optionalResult.isPresent() && optionalResult.get().errorText.isPresent()) {
            return optionalResult.get().errorText;
        }

        return Optional.absent();
    }

    public static HandlerResult emptyResult() {
        return new HandlerResult(HandlerType.NONE, EMPTY_COMMAND, Collections.emptyMap(), Optional.absent(), Optional.absent());
    }

    public static HandlerResult withRoomConditionResult(final HandlerType handlerType, final String command,
                                                        final GenericResult result,
                                                        final RoomConditionResult roomConditionResult) {
        final Map<String, String> response = Maps.newHashMap();
        response.putAll(responseParameters(result));
        response.put("result", Outcome.OK.getValue());
        response.put("sensor", roomConditionResult.sensorName);
        response.put("value", roomConditionResult.sensorValue);
        response.put("unit", roomConditionResult.sensorUnit);
        response.put("text", String.format("The %s in your room is %s %s",
                roomConditionResult.sensorName,
                roomConditionResult.sensorValue,
                roomConditionResult.sensorUnit));

        return new HandlerResult(handlerType, command, response, Optional.of(result), Optional.of(roomConditionResult));
    }
}
