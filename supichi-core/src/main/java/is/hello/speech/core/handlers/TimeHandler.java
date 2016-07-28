package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.models.TimeZoneHistory;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;
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

        if (optionalCommand.isPresent()) {
            final int offsetMillis = getTimeZoneOffsetMillis(accountId);
            final DateTime now = DateTime.now(DateTimeZone.UTC).plusMillis(offsetMillis);

            final String currentTime = now.toString("HH_mm");
            LOGGER.debug("action=get-current-time now={} string={} offset={} account_id={}", now.toString(), currentTime, offsetMillis, accountId);
            response.put("result", HandlerResult.Outcome.OK.getValue());
            response.put("time", currentTime);
        }

        return new HandlerResult(HandlerType.TIME_REPORT, response);
    }

    private int getTimeZoneOffsetMillis(final Long accountId) {
        final Optional<TimeZoneHistory> tzHistory = this.timeZoneHistoryDAODynamoDB.getCurrentTimeZone(accountId);
        if (tzHistory.isPresent()) {
            return tzHistory.get().offsetMillis;
        }

        return TimeZoneHistory.FALLBACK_OFFSET_MILLIS;
    }
}
