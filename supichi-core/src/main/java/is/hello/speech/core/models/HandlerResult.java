package is.hello.speech.core.models;

import com.google.common.collect.Maps;

import java.util.Map;

/**
 * Created by ksg on 7/25/16
 */
public class HandlerResult {
    public static final String EMPTY_COMMAND = "NONE";
    private static final String TEXT_RESPONSE_FIELD = "text";
    public enum Outcome {
        OK("ok"),
        FAIL("fail");

        protected String value;

        Outcome(final String value) { this.value = value; }

        public String getValue() { return this.value; }

        public static Outcome fromString(final String text) {
            if (text != null) {
                for (final Outcome outcome : Outcome.values()) {
                    if (text.equalsIgnoreCase(outcome.toString()))
                        return outcome;
                }
            }
            return Outcome.FAIL;
        }
    }

    public final HandlerType handlerType;
    public final String command;
    public Map<String, String> responseParameters;

    public HandlerResult(final HandlerType handlerType, final String command, final Map<String, String> responseParameters) {
        this.handlerType = handlerType;
        this.command = command;
        this.responseParameters = responseParameters;
    }

    public String getResponseText() {
        if (responseParameters.containsKey(TEXT_RESPONSE_FIELD)) {
            return responseParameters.get(TEXT_RESPONSE_FIELD);
        } else {
            return "";
        }
    }
    public static HandlerResult emptyResult() {
        return new HandlerResult(HandlerType.NONE, EMPTY_COMMAND, Maps.newHashMap());
    }
}
