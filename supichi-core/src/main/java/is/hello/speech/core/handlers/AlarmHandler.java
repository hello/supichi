package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.models.AnnotatedTranscript;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;
import is.hello.speech.core.response.SupichiResponseType;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by ksg on 6/17/16
 */
public class AlarmHandler extends BaseHandler {

    public AlarmHandler(SpeechCommandDAO speechCommandDAO) {
        super("alarm", speechCommandDAO, getAvailableActions());
    }

    private static final String CANCEL_ALARM_REGEX = "(cancel|delete|remove|unset).*(?:alarm)(s?)";
    private static final Pattern CANCEL_ALARM_PATTERN = Pattern.compile(CANCEL_ALARM_REGEX);

    private static final String SET_ALARM_REGEX = "((set).*(?:alarm))|(wake me)";
    private static final Pattern SET_ALARM__PATTERN = Pattern.compile(SET_ALARM_REGEX);

    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();

        tempMap.put(SET_ALARM_REGEX, SpeechCommand.ALARM_SET);
        tempMap.put("set smart alarm", SpeechCommand.ALARM_SET);
        tempMap.put("set alarm", SpeechCommand.ALARM_SET);
        tempMap.put("set an alarm", SpeechCommand.ALARM_SET);
        tempMap.put("wake me", SpeechCommand.ALARM_SET);
        tempMap.put("wake me up", SpeechCommand.ALARM_SET);

        tempMap.put(CANCEL_ALARM_REGEX, SpeechCommand.ALARM_DELETE);
        tempMap.put("cancel alarm", SpeechCommand.ALARM_DELETE);
        tempMap.put("unset alarm", SpeechCommand.ALARM_DELETE);
        tempMap.put("remove alarm", SpeechCommand.ALARM_DELETE);
        tempMap.put("delete alarm", SpeechCommand.ALARM_DELETE);
        return tempMap;
    }

    @Override
    Optional<SpeechCommand> getCommand(final String text) {
        final Matcher cancelMatcher = CANCEL_ALARM_PATTERN.matcher(text);
        if (cancelMatcher.find()) {
            return Optional.of (SpeechCommand.ALARM_DELETE);
        }

        final Matcher setMatcher = SET_ALARM__PATTERN.matcher(text);
        if (setMatcher.find()) {
            return Optional.of(SpeechCommand.ALARM_SET);
        }

        return Optional.absent();
    }

    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final String senseId, final Long accountId) {
        // TODO
        final Optional<SpeechCommand> optionalCommand = getCommand(annotatedTranscript.transcript);
        final Map<String, String> response;
        String command = HandlerResult.EMPTY_COMMAND;

        if (optionalCommand.isPresent()) {
            command = optionalCommand.get().getValue();

            if (optionalCommand.get().equals(SpeechCommand.ALARM_SET)) {
                response = setAlarm(accountId, senseId, annotatedTranscript);
            } else {
                response = cancelAlarm(accountId, senseId, annotatedTranscript);
            }

        } else {
            response = Maps.newHashMap();
            response.put("result", HandlerResult.Outcome.OK.getValue());
            response.put("text", "Ok, alarm set.");
        }

        return new HandlerResult(HandlerType.ALARM, command, response);
    }

    private Map<String, String> setAlarm(final Long accountId, final String senseId, final AnnotatedTranscript annotatedTranscript) {
        final Map<String, String> response = Maps.newHashMap();
        response.put("result", HandlerResult.Outcome.OK.getValue());
        response.put("text", "Ok, your alarm is set.");
        return response;

    }

    private Map<String, String> cancelAlarm(final Long accountId, final String senseId, final AnnotatedTranscript annotatedTranscript) {
        final Map<String, String> response = Maps.newHashMap();

        response.put("result", HandlerResult.Outcome.OK.getValue());
        response.put("text", "Ok, your alarm is canceled.");
        return response;
    }

    @Override
    public Integer matchAnnotations(final AnnotatedTranscript annotatedTranscript) {
        return annotatedTranscript.times.size();
    }

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }
}