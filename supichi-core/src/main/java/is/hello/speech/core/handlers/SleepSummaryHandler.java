package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.SleepStatsDAODynamoDB;
import com.hello.suripu.core.models.AggregateSleepStats;
import com.hello.suripu.core.models.TimelineResult;
import com.hello.suripu.core.util.DateTimeUtil;
import com.hello.suripu.core.util.TimelineUtils;
import com.hello.suripu.coredropwizard.timeline.InstrumentedTimelineProcessor;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.handlers.results.GenericResult;
import is.hello.speech.core.handlers.results.Outcome;
import is.hello.speech.core.models.AnnotatedTranscript;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;
import is.hello.speech.core.models.VoiceRequest;
import is.hello.speech.core.response.SupichiResponseType;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

import static is.hello.speech.core.handlers.ErrorText.NO_TIMEZONE;
import static is.hello.speech.core.models.HandlerResult.EMPTY_COMMAND;

/**
 * Created by ksg on 6/17/16
 */
public class SleepSummaryHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SleepSummaryHandler.class);

    public static final String ERROR_NO_SLEEP_STATS = "Sorry, you have no sleep data for last night.";
    public static final String ERROR_NO_TIMEZONE = "Sorry, we're unable to retrieve your sleep score. Please set your timezone in the app.";

    private final SleepStatsDAODynamoDB sleepStatsDAO;
    private final InstrumentedTimelineProcessor timelineProcessor;

    public SleepSummaryHandler(final SpeechCommandDAO speechCommandDAO, final SleepStatsDAODynamoDB sleepStatsDAO, final InstrumentedTimelineProcessor timelineProcessor) {
        super("sleep-summary", speechCommandDAO, getAvailableActions());
        this.sleepStatsDAO = sleepStatsDAO;
        this.timelineProcessor = timelineProcessor;
    }


    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("sleep score", SpeechCommand.SLEEP_SCORE);
        tempMap.put("sleep summary", SpeechCommand.SLEEP_SUMMARY);
        tempMap.put("how was my sleep", SpeechCommand.SLEEP_SUMMARY);
        return tempMap;
    }

    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        // TODO
        final Optional<SpeechCommand> optionalCommand = getCommand(annotatedTranscript.transcript);
        final Map<String, String> response = Maps.newHashMap();

        final Long accountId = request.accountId;

        if (optionalCommand.isPresent()) {
            final GenericResult genericResult;
            final SpeechCommand command = optionalCommand.get();

            switch (command) {
                case SLEEP_SCORE:
                    genericResult = getSleepScore(accountId, annotatedTranscript);
                    break;
                case SLEEP_SUMMARY:
                    genericResult = getSleepSummary(accountId, annotatedTranscript);
                    break;
                default:
                    response.put("result", Outcome.FAIL.getValue());
                    response.put("error", "no sleep data");
                    return new HandlerResult(HandlerType.SLEEP_SUMMARY, EMPTY_COMMAND, response, Optional.absent());
            }

            response.put("result", genericResult.outcome.getValue());

            if (genericResult.errorText.isPresent()) {
                response.put("error", genericResult.errorText.get());
                if (genericResult.responseText.isPresent()) {
                    response.put("text", genericResult.responseText());
                }
            } else {
                response.put("text", genericResult.responseText());
            }

            return new HandlerResult(HandlerType.ALARM, command.getValue(), response, Optional.of(genericResult));
        }

        response.put("result", Outcome.FAIL.getValue());
        response.put("error", "no sleep data");
        return new HandlerResult(HandlerType.SLEEP_SUMMARY, EMPTY_COMMAND, response, Optional.absent());

    }

    private GenericResult getSleepSummary(final Long accountId, final AnnotatedTranscript annotatedTranscript) {
        if (!annotatedTranscript.timeZoneOptional.isPresent()) {
            LOGGER.error("error=no-alarm-set reason=no-timezone account_id={}", accountId);
            return GenericResult.failWithResponse(NO_TIMEZONE, ERROR_NO_TIMEZONE);
        }

        final DateTimeZone timezoneId = DateTimeZone.forTimeZone(annotatedTranscript.timeZoneOptional.get());
        final Optional<AggregateSleepStats> optionalSleepStat = getSleepStat(accountId, timezoneId);
        if (!optionalSleepStat.isPresent()) {
            return GenericResult.failWithResponse("no sleep summary", ERROR_NO_SLEEP_STATS);
        }

        final TimelineUtils timelineUtils = new TimelineUtils(UUID.randomUUID());
        final String summary = timelineUtils.generateMessage(optionalSleepStat.get().sleepStats, 0, 0);
        return GenericResult.ok(summary.replace("**", ""));
    }

    private GenericResult getSleepScore(final Long accountId, final AnnotatedTranscript annotatedTranscript) {
        if (!annotatedTranscript.timeZoneOptional.isPresent()) {
            LOGGER.error("error=no-alarm-set reason=no-timezone account_id={}", accountId);
            return GenericResult.failWithResponse(NO_TIMEZONE, ERROR_NO_TIMEZONE);
        }

        final DateTimeZone timezoneId = DateTimeZone.forTimeZone(annotatedTranscript.timeZoneOptional.get());
        final Optional<AggregateSleepStats> optionalSleepStat = getSleepStat(accountId, timezoneId);
        if (!optionalSleepStat.isPresent()) {
            return GenericResult.failWithResponse("no sleep data", ERROR_NO_SLEEP_STATS);
        }

        final String response = String.format("Your sleep score was %d last night", optionalSleepStat.get().sleepScore);
        return GenericResult.ok(response);
    }

    private Optional<AggregateSleepStats> getSleepStat(final Long accountId, final DateTimeZone timezoneId) {
        final DateTime localToday = DateTime.now(DateTimeZone.UTC).withZone(timezoneId).withTimeAtStartOfDay();
        final String lastNightDate = DateTimeUtil.dateToYmdString(localToday.minusDays(1));

        final Optional<AggregateSleepStats> optionalSleepStat = sleepStatsDAO.getSingleStat(accountId, lastNightDate);
        if (!optionalSleepStat.isPresent()) {
            // TODO: compute timeline
            final InstrumentedTimelineProcessor newTimelineProcessor = timelineProcessor.copyMeWithNewUUID(UUID.randomUUID());
            final TimelineResult result = newTimelineProcessor.retrieveTimelinesFast(accountId, localToday.minusDays(1), Optional.absent());

            if (!result.timelines.isEmpty() && result.timelines.get(0).score > 0 && result.timelines.get(0).statistics.isPresent()) {
                final AggregateSleepStats aggStats = new AggregateSleepStats.Builder()
                        .withSleepStats(result.timelines.get(0).statistics.get())
                        .withSleepScore(result.timelines.get(0).score).build();
                return Optional.of(aggStats);
            }
            return Optional.absent();
        }

        return optionalSleepStat;
    }

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }
}