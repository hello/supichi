package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.handlers.results.Outcome;
import is.hello.speech.core.models.AnnotatedTranscript;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;
import is.hello.speech.core.models.VoiceRequest;
import is.hello.speech.core.response.SupichiResponseType;

import java.util.Map;

/**
 * Created by ksg on 6/17/16
 */
public class TimelineHandler extends BaseHandler {

    public TimelineHandler(SpeechCommandDAO speechCommandDAO) {
        super("timeline", speechCommandDAO, getAvailableActions());
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
//        tempMap.put("sleep last", SpeechCommand.TIMELINE);
        tempMap.put("my timeline", SpeechCommand.TIMELINE);
        return tempMap;
    }

    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        // TODO
        final String text = annotatedTranscript.transcript;

        final Optional<SpeechCommand> optionalCommand = getCommand(text);

        final Map<String, String> response = Maps.newHashMap();

        String command = HandlerResult.EMPTY_COMMAND;
        if (optionalCommand.isPresent()) {
            command = optionalCommand.get().getValue();
            response.put("result", Outcome.OK.getValue());
            response.put("text", "Your sleep timeline is being computed");
        }

        return new HandlerResult(HandlerType.ALARM, command, response, Optional.absent());
    }

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }
}