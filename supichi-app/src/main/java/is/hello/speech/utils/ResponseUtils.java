package is.hello.speech.utils;

import is.hello.speech.core.models.HandlerResult;

/**
 * Created by ksg on 8/3/16
 */
public class ResponseUtils {
    public static HandlerResult.Outcome getOutcome (final HandlerResult handlerResult) {
        final String outcomeString = handlerResult.responseParameters.get("result");
        return HandlerResult.Outcome.fromString(outcomeString);
    }

}
