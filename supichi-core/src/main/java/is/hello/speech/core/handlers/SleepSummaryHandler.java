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

import static is.hello.speech.core.handlers.ErrorText.NO_SLEEP_DATA;
import static is.hello.speech.core.handlers.ErrorText.NO_SLEEP_SUMMARY;
import static is.hello.speech.core.handlers.ErrorText.NO_TIMEZONE;
import static is.hello.speech.core.models.SpeechCommand.SLEEP_SCORE;
import static is.hello.speech.core.models.SpeechCommand.SLEEP_SUMMARY;

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
        tempMap.put("sleep summary", SLEEP_SUMMARY);
        tempMap.put("how was my sleep", SLEEP_SUMMARY);
        return tempMap;
    }

    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        // TODO
        final Optional<SpeechCommand> optionalCommand = getCommand(annotatedTranscript.transcript);
        final Map<String, String> response = Maps.newHashMap();

        final Long accountId = request.accountId;

        if (optionalCommand.isPresent()) {
            final SpeechCommand command = optionalCommand.get();
            switch (command) {
                case SLEEP_SCORE:
                    return getSleepScore(accountId, annotatedTranscript);
                case SLEEP_SUMMARY:
                    return getSleepSummary(accountId, annotatedTranscript);
            }
        }

        return new HandlerResult(HandlerType.SLEEP_SUMMARY, HandlerResult.EMPTY_COMMAND, GenericResult.failWithResponse(NO_SLEEP_DATA, ERROR_NO_SLEEP_STATS));
    }

    private HandlerResult getSleepSummary(final Long accountId, final AnnotatedTranscript annotatedTranscript) {
        if (!annotatedTranscript.timeZoneOptional.isPresent()) {
            LOGGER.error("error=no-sleep-summary reason=no-timezone account_id={}", accountId);
            return new HandlerResult(HandlerType.SLEEP_SUMMARY, SLEEP_SUMMARY.getValue(), GenericResult.failWithResponse(NO_TIMEZONE, ERROR_NO_TIMEZONE));
        }

        final DateTimeZone timezoneId = DateTimeZone.forTimeZone(annotatedTranscript.timeZoneOptional.get());
        final Optional<AggregateSleepStats> optionalSleepStat = getSleepStat(accountId, timezoneId);
        if (!optionalSleepStat.isPresent()) {
            return new HandlerResult(HandlerType.SLEEP_SUMMARY, SLEEP_SUMMARY.getValue(), GenericResult.failWithResponse(NO_SLEEP_SUMMARY, ERROR_NO_SLEEP_STATS));
        }

        final TimelineUtils timelineUtils = new TimelineUtils(UUID.randomUUID());
        final String summary = timelineUtils.generateMessage(optionalSleepStat.get().sleepStats, 0, 0);
        return new HandlerResult(HandlerType.SLEEP_SUMMARY, SLEEP_SUMMARY.getValue(), GenericResult.ok(summary.replace("**", "")));
    }


    private HandlerResult getSleepScore(final Long accountId, final AnnotatedTranscript annotatedTranscript) {
        if (!annotatedTranscript.timeZoneOptional.isPresent()) {
            LOGGER.error("error=no-sleep-score reason=no-timezone account_id={}", accountId);

            return new HandlerResult(HandlerType.SLEEP_SUMMARY, SLEEP_SCORE.getValue(), GenericResult.failWithResponse(NO_TIMEZONE, ERROR_NO_TIMEZONE));
        }

        final DateTimeZone timezoneId = DateTimeZone.forTimeZone(annotatedTranscript.timeZoneOptional.get());
        final Optional<AggregateSleepStats> optionalSleepStat = getSleepStat(accountId, timezoneId);
        if (!optionalSleepStat.isPresent()) {
            return new HandlerResult(HandlerType.SLEEP_SUMMARY, SLEEP_SCORE.getValue(), GenericResult.failWithResponse(NO_SLEEP_DATA, ERROR_NO_SLEEP_STATS));
        }

        final String response = String.format("Your sleep score was %d last night", optionalSleepStat.get().sleepScore);
        return new HandlerResult(HandlerType.SLEEP_SUMMARY, SLEEP_SCORE.getValue(), GenericResult.ok(response));
    }

    private Optional<AggregateSleepStats> getSleepStat(final Long accountId, final DateTimeZone timezoneId) {
        final DateTime localToday = DateTime.now(DateTimeZone.UTC).withZone(timezoneId).withTimeAtStartOfDay();
        final String lastNightDate = DateTimeUtil.dateToYmdString(localToday.minusDays(1));

        final Optional<AggregateSleepStats> optionalSleepStat = sleepStatsDAO.getSingleStat(accountId, lastNightDate);

        if (optionalSleepStat.isPresent()) {
            return optionalSleepStat;
        }

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

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }
}