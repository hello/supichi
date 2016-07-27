package is.hello.speech.core.models;

import com.google.common.collect.Maps;

import java.util.Map;

/**
 * Created by ksg on 7/25/16
 */
public class HandlerResult {
    public enum Outcome {
        OK("ok"),
        FAIL("fail");

        protected String value;
        Outcome(final String value) { this.value = value; }
        public String getValue() { return this.value; }
    }

    public final HandlerType handlerType;
    public Map<String, String> responseParameters;

    public HandlerResult(final HandlerType handlerType, final Map<String, String> responseParameters) {
        this.handlerType = handlerType;
        this.responseParameters = responseParameters;
    }

    public static HandlerResult emptyResult() {
        return new HandlerResult(HandlerType.NONE, Maps.newHashMap());
    }
}
