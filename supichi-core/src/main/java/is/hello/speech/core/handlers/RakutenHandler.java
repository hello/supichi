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
public class RakutenHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RakutenHandler.class);


    private final SpeechCommandDAO speechCommandDAO;

    public RakutenHandler(final SpeechCommandDAO speechCommandDAO) {
        super("rakuten", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("special offer", SpeechCommand.RAKUTEN);
        tempMap.put("special offers", SpeechCommand.RAKUTEN);
        tempMap.put("work tomorrow", SpeechCommand.RAKUTEN);
        tempMap.put("harry potter", SpeechCommand.RAKUTEN);
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(String text, String senseId, Long accountId) {
        final Optional<SpeechCommand> optionalCommand = getCommand(text); // TODO: ensure that only valid commands are returned
        final Map<String, String> response = Maps.newHashMap();

        if (optionalCommand.isPresent()) {

            if (text.equalsIgnoreCase("work tomorrow")) {
                response.put("result", HandlerResult.Outcome.OK.getValue());
                response.put("answer", "work_tomorrow");
                response.put("text", "Your smart alarm is set for 6am, and I will order you a Lyft for 7am.");

            } else if (text.equalsIgnoreCase("special offers") || text.equalsIgnoreCase("special offer")) {
                response.put("result", HandlerResult.Outcome.OK.getValue());
                response.put("answer", "special_offers_ebates");
                response.put("text", "Yes, you can get 3% cash back at nigh kee right now. It was only 1.5% last week.");

            } else if (text.equalsIgnoreCase("harry potter")) {
                response.put("result", HandlerResult.Outcome.OK.getValue());
                response.put("answer", "harry_potter");
                response.put("text", "Harry Potter and the Cursed Child is ready to read on your Aura One.");

            }
        }
        return new HandlerResult(HandlerType.RAKUTEN, response);
    }

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }

}
