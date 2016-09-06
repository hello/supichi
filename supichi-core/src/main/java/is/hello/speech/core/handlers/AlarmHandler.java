package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;
import is.hello.speech.core.response.SupichiResponseType;

import java.util.Map;

/**
 * Created by ksg on 6/17/16
 */
public class AlarmHandler extends BaseHandler {

    public AlarmHandler(SpeechCommandDAO speechCommandDAO) {
        super("alarm", speechCommandDAO, getAvailableActions());
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("set alarm", SpeechCommand.ALARM_SET);
        tempMap.put("unset alarm", SpeechCommand.ALARM_DELETE);
        tempMap.put("remove alarm", SpeechCommand.ALARM_DELETE);
        tempMap.put("delete alarm", SpeechCommand.ALARM_DELETE);
        tempMap.put("wake me", SpeechCommand.ALARM_SET);
        tempMap.put("me up", SpeechCommand.ALARM_SET);
        return tempMap;
    }

    @Override
    public HandlerResult executeCommand(final String text, final String senseId, final Long accountId) {
        // TODO
        final Optional<SpeechCommand> optionalCommand = getCommand(text);

        final Map<String, String> response = Maps.newHashMap();

        String command = HandlerResult.EMPTY_COMMAND;
        if (optionalCommand.isPresent()) {
            command = optionalCommand.get().getValue();
            response.put("result", HandlerResult.Outcome.OK.getValue());
            response.put("text", "Ok, alarm set.");
        }

        return new HandlerResult(HandlerType.ALARM, command, response);
    }

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }
}