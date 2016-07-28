package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;


/**
 * Created by ksg on 6/17/16
 */
public class TriviaHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TriviaHandler.class);

    private static final String DEFAULT_SENSOR_UNIT = "f";
    private static final Float NO_SOUND_FILL_VALUE_DB = (float) 35; // Replace with this value when Sense isn't capturing audio


    private final SpeechCommandDAO speechCommandDAO;

    public TriviaHandler(final SpeechCommandDAO speechCommandDAO) {
        super("time_report", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("the president", SpeechCommand.TRIVA);
        tempMap.put("hello ceo", SpeechCommand.TRIVA);
        tempMap.put("hello co", SpeechCommand.TRIVA);
        tempMap.put("next president", SpeechCommand.TRIVA);
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(String text, String senseId, Long accountId) {
        final Optional<SpeechCommand> optionalCommand = getCommand(text); // TODO: ensure that only valid commands are returned
        final Map<String, String> response = Maps.newHashMap();

        if (optionalCommand.isPresent()) {

            if (text.equalsIgnoreCase("the president")) {
                response.put("result", HandlerResult.Outcome.OK.getValue());
                response.put("answer", "president_obama");
                response.put("text", "The current president of the United States is Barack Obama.");

            } else if (text.equalsIgnoreCase("hello ceo")) {
                response.put("result", HandlerResult.Outcome.OK.getValue());
                response.put("answer", "hello_ceo_james");
                response.put("text", "The CEO of Hello Inc. will always be James Proud.");
            } else if (text.equalsIgnoreCase("next president")) {
                response.put("result", HandlerResult.Outcome.OK.getValue());
                response.put("answer", "next_president");
                response.put("text", "The next president of the United States will either be Hillary Clinton, or Donald Trump.");
            }
        }
        return new HandlerResult(HandlerType.TRIVIA, response);
    }
}
