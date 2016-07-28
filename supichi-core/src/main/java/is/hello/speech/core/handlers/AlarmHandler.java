package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.SpeechCommand;

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
        return tempMap;
    }

    @Override
    public HandlerResult executeCommand(final String text, final String senseId, final Long accountId) {
        // TODO
        final Optional<SpeechCommand> optionalCommand = getCommand(text);
        return HandlerResult.emptyResult();
    }
}