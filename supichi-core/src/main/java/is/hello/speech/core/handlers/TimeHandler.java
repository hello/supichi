package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.models.TimeZoneHistory;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;
import is.hello.speech.core.models.AnnotatedTranscript;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;


/**
 * Created by ksg on 6/17/16
 */
public class TimeHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimeHandler.class);


    private final SpeechCommandDAO speechCommandDAO;
    private final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB;

    public TimeHandler(final SpeechCommandDAO speechCommandDAO,
                       final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB ) {
        super("time_report", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
        this.timeZoneHistoryDAODynamoDB = timeZoneHistoryDAODynamoDB;
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("the time", SpeechCommand.TIME_REPORT);
        tempMap.put("what time", SpeechCommand.TIME_REPORT);
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(String text, String senseId, Long accountId) {
        final Optional<SpeechCommand> optionalCommand = getCommand(text); // TODO: ensure that only valid commands are returned
        final Map<String, String> response = Maps.newHashMap();
        String command = HandlerResult.EMPTY_COMMAND;

        if (optionalCommand.isPresent()) {
            command = optionalCommand.get().getValue();
            final int offsetMillis = getTimeZoneOffsetMillis(accountId);
            final DateTime now = DateTime.now(DateTimeZone.UTC);
            final DateTime localNow = now.plusMillis(offsetMillis);

            final String currentTime = localNow.toString("HH_mm");
            LOGGER.debug("action=get-current-time now={} local_now={} string={} offset={} account_id={}", now.toString(), localNow.toString(), currentTime, offsetMillis, accountId);
            response.put("result", HandlerResult.Outcome.OK.getValue());
            response.put("time", currentTime);
            response.put("text", String.format("The time is %s", currentTime));
        }

        return new HandlerResult(HandlerType.TIME_REPORT, command, response);
    }

    @Override
    public Integer matchAnnotations(final AnnotatedTranscript annotatedTranscript) {
        // TODO: add Location
        return NO_ANNOTATION_SCORE;
    }

    private int getTimeZoneOffsetMillis(final Long accountId) {
        final Optional<TimeZoneHistory> tzHistory = this.timeZoneHistoryDAODynamoDB.getCurrentTimeZone(accountId);
        if (tzHistory.isPresent()) {
            return tzHistory.get().offsetMillis;
        }

        return -25200000; // PDT
    }
}
