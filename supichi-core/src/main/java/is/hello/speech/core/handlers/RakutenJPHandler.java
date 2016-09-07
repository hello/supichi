package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;
import is.hello.speech.core.response.SupichiResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by ksg on 9/1/16
 */
public class RakutenJPHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RakutenJPHandler.class);


    private final SpeechCommandDAO speechCommandDAO;

    public RakutenJPHandler(final SpeechCommandDAO speechCommandDAO) {
        super("rakuten_jp", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("other languages", SpeechCommand.RAKUTEN_JP);
        tempMap.put("other language", SpeechCommand.RAKUTEN_JP);
        tempMap.put("speak other", SpeechCommand.RAKUTEN_JP);
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(String text, String senseId, Long accountId) {
        final Optional<SpeechCommand> optionalCommand = getCommand(text); // TODO: ensure that only valid commands are returned
        final Map<String, String> response = Maps.newHashMap();
        String command = HandlerResult.EMPTY_COMMAND;

        if (optionalCommand.isPresent()) {
            command = optionalCommand.get().getValue();
            if (text.equalsIgnoreCase("other languages") || text.equalsIgnoreCase("speak other") || text.equalsIgnoreCase("other language")) {
                response.put("result", HandlerResult.Outcome.OK.getValue());
                response.put("answer", "other_languages");
                response.put("text", "私は、ほぼすべての言語をサポートすることができるしています.");
            }
        }
        return new HandlerResult(HandlerType.RAKUTEN_JP, command, response);
    }

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON_JP;
    }

}
