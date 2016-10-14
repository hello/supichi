package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.models.TimeZoneHistory;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.handlers.results.GenericResult;
import is.hello.speech.core.models.AnnotatedTranscript;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;
import is.hello.speech.core.models.VoiceRequest;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

import static is.hello.speech.core.handlers.ErrorText.NO_TIMEZONE;


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
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        final String text = annotatedTranscript.transcript;

        final Optional<SpeechCommand> optionalCommand = getCommand(text); // TODO: ensure that only valid commands are returned
        String command = HandlerResult.EMPTY_COMMAND;

        Optional<GenericResult> result = Optional.absent();
        if (optionalCommand.isPresent()) {
            command = optionalCommand.get().getValue();
            final Optional<String> optionalTimeZoneId = getTimeZoneOffsetMillis(request.accountId);
            if (!optionalTimeZoneId.isPresent()) {
                result = Optional.of(GenericResult.fail(NO_TIMEZONE));
            } else {

                final DateTimeZone userTimeZone = DateTimeZone.forID(optionalTimeZoneId.get());
                final DateTime localNow = DateTime.now(DateTimeZone.UTC).withZone(userTimeZone);

                final String currentTime = localNow.toString("HH_mm");
                LOGGER.debug("action=get-current-time local_now={} string={} time_zone={} account_id={}",
                        localNow.toString(), currentTime, userTimeZone.toString(), request.accountId);

                result = Optional.of(GenericResult.ok(currentTime));
            }
        }

        return new HandlerResult(HandlerType.TIME_REPORT, command, Collections.EMPTY_MAP, result);
    }

    @Override
    public Integer matchAnnotations(final AnnotatedTranscript annotatedTranscript) {
        // TODO: add Location
        return NO_ANNOTATION_SCORE;
    }

    private Optional<String> getTimeZoneOffsetMillis(final Long accountId) {
        final Optional<TimeZoneHistory> tzHistory = this.timeZoneHistoryDAODynamoDB.getCurrentTimeZone(accountId);
        if (tzHistory.isPresent()) {
            return Optional.of(tzHistory.get().timeZoneId);
        }

        return Optional.absent();
    }
}
