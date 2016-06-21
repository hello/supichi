package is.hello.speech.core.handlers;

import java.util.Set;

/**
 * Created by ksg on 6/20/16
 */
public interface BaseHandler {
    Set<String> getRelevantCommands();
    Boolean executionCommand(String text, String senseId, Long accountId);
}
