package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.SpeechCommand;
import is.hello.speech.core.response.SupichiResponseType;

import java.util.Map;
import java.util.Set;

/**
 * Created by ksg on 6/20/16
 */
public abstract class BaseHandler {
    private final String handlerName;
    private final ImmutableMap<String, SpeechCommand> commandMap;
    private final SpeechCommandDAO speechCommandDAO;

    BaseHandler(final String handlerName, final SpeechCommandDAO speechCommandDAO, final Map<String, SpeechCommand> commandMap) {
        this.handlerName = handlerName;
        this.speechCommandDAO = speechCommandDAO;
        this.commandMap = ImmutableMap.copyOf(commandMap);
    }

    public Set<String> getRelevantCommands() {
        return this.commandMap.keySet();
    }

    Optional<SpeechCommand> getCommand(final String text) {
        if (commandMap.containsKey(text)) {
            return Optional.of(commandMap.get(text));
        }
        return Optional.absent();
    }

    public abstract HandlerResult executeCommand(final String text, final String senseId, final Long accountId);

    public SupichiResponseType responseType() {
        return SupichiResponseType.S3;
    };
}
