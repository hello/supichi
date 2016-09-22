package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hello.suripu.core.alarm.AlarmProcessor;
import com.hello.suripu.core.db.AlarmDAODynamoDB;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.models.Alarm;
import com.hello.suripu.core.models.AlarmSound;
import com.hello.suripu.core.models.AlarmSource;
import com.hello.suripu.core.models.RingTime;
import com.hello.suripu.core.models.UserInfo;
import com.hello.suripu.core.processors.RingProcessor;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.handlers.results.GenericResult;
import is.hello.speech.core.handlers.results.Outcome;
import is.hello.speech.core.models.AnnotatedTranscript;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;
import is.hello.speech.core.models.annotations.TimeAnnotation;
import is.hello.speech.core.response.SupichiResponseType;
import jersey.repackaged.com.google.common.collect.Sets;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static is.hello.speech.core.models.HandlerResult.EMPTY_COMMAND;

/**
 * Created by ksg on 6/17/16
 */
public class AlarmHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmHandler.class);

    private static final String CANCEL_ALARM_REGEX = "(cancel|delete|remove|unset).*(?:alarm)(s?)";
    private static final Pattern CANCEL_ALARM_PATTERN = Pattern.compile(CANCEL_ALARM_REGEX);

    private static final String SET_ALARM_REGEX = "((set).*(?:alarm))|(wake me)";
    private static final Pattern SET_ALARM__PATTERN = Pattern.compile(SET_ALARM_REGEX);

    private static final AlarmSound DEFAULT_ALARM_SOUND = new AlarmSound(5, "Dusk", "");

    private final AlarmProcessor alarmProcessor;
    private final MergedUserInfoDynamoDB mergedUserInfoDynamoDB;

    AlarmHandler(final SpeechCommandDAO speechCommandDAO, final AlarmDAODynamoDB alarmDAODynamoDB, final MergedUserInfoDynamoDB mergedUserInfoDynamoDB) {
        super("alarm", speechCommandDAO, getAvailableActions());
        this.alarmProcessor = new AlarmProcessor(alarmDAODynamoDB, mergedUserInfoDynamoDB);
        this.mergedUserInfoDynamoDB = mergedUserInfoDynamoDB;
    }


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
        final Map<String, String> response = Maps.newHashMap();


        if (!optionalCommand.isPresent()) {
            response.put("result", Outcome.FAIL.getValue());
            response.put("error", "no alarm set");
            return new HandlerResult(HandlerType.ALARM, EMPTY_COMMAND, response, Optional.absent());
        }

        final String command = optionalCommand.get().getValue();

        final GenericResult alarmResult;
        if (optionalCommand.get().equals(SpeechCommand.ALARM_SET)) {
            alarmResult = setAlarm(accountId, senseId, annotatedTranscript);
        } else {
            alarmResult = cancelAlarm(accountId, senseId, annotatedTranscript);
        }

        response.put("result", alarmResult.outcome.getValue());
        if (alarmResult.errorText.isPresent()) {
            response.put("error", alarmResult.errorText.get());
        } else {
            response.put("text", alarmResult.responseText());
        }

        return new HandlerResult(HandlerType.ALARM, command, response, Optional.of(alarmResult));
    }

    /**
     * set alarm for the next matching time
     */
    private GenericResult setAlarm(final Long accountId, final String senseId, final AnnotatedTranscript annotatedTranscript) {
        if (annotatedTranscript.times.isEmpty()) {
            return new GenericResult(Outcome.FAIL, Optional.of("no time give"), Optional.absent());
        }

        if (!annotatedTranscript.timeZoneOptional.isPresent()) {
            return new GenericResult(Outcome.FAIL, Optional.of("no timezone"), Optional.absent());
        }

        final TimeAnnotation timeAnnotation = annotatedTranscript.times.get(0); // note time is in utc, need to convert
        final DateTimeZone timezoneId = DateTimeZone.forTimeZone(annotatedTranscript.timeZoneOptional.get());
        final DateTime annotatedTimeUTC = new DateTime(timeAnnotation.dateTime(), DateTimeZone.UTC);
        final DateTime now = DateTime.now(DateTimeZone.UTC);

        final DateTime alarmTime =  (annotatedTimeUTC.isAfter(now)) ? annotatedTimeUTC.withZone(timezoneId) : annotatedTimeUTC.plusDays(1).withZone(timezoneId);
        LOGGER.debug("action=create-alarm-time account_id={} annotation_time={} now={} final_alarm={}",
                accountId, annotatedTimeUTC.toString(), now, alarmTime.toString());

        final Alarm newAlarm = new Alarm.Builder()
                .withYear(alarmTime.getYear())
                .withMonth(alarmTime.getMonthOfYear())
                .withDay(alarmTime.getDayOfMonth())
                .withHour(alarmTime.getHourOfDay())
                .withMinute(alarmTime.getMinuteOfHour())
                .withDayOfWeek(Sets.newHashSet(alarmTime.getDayOfWeek()))
                .withIsRepeated(false)
                .withAlarmSound(DEFAULT_ALARM_SOUND)
                .withIsEnabled(true)
                .withIsEditable(true)
                .withIsSmart(false)
                .withSource(AlarmSource.VOICE_SERVICE)
                .build();


        final List<Alarm> alarms = Lists.newArrayList();
        alarms.addAll(alarmProcessor.getAlarms(accountId, senseId));
        alarms.add(newAlarm);

        try {
            alarmProcessor.setAlarms(accountId, senseId, alarms);
        } catch (Exception exception) {
            return new GenericResult(Outcome.FAIL, Optional.of(exception.getMessage()),
                    Optional.of("Sorry, we're unable to set your alarm. Please try again later"));
        }

        final String alarmDay = (annotatedTimeUTC.isAfter(now)) ? "today" : "tomorrow";
        final String responseText = String.format("Ok, your alarm is set for %s %s",
                alarmTime.toString(DateTimeFormat.forPattern("hh:mm a")), alarmDay);
        return new GenericResult(Outcome.OK, Optional.absent(), Optional.of (responseText));
    }

    /**
     * only allow non-repeating, next-occurring alarm to be canceled
     */
    private GenericResult cancelAlarm(final Long accountId, final String senseId, final AnnotatedTranscript annotatedTranscript) {

        final Optional<UserInfo> alarmInfoOptional = this.mergedUserInfoDynamoDB.getInfo(senseId, accountId);
        if (!alarmInfoOptional.isPresent()) {
            return new GenericResult(Outcome.FAIL, Optional.of("no user info"), Optional.absent());
        }

        final UserInfo userInfo = alarmInfoOptional.get();
        final RingTime nextRingTime = RingProcessor.getRingTimeFromAlarmInfo(userInfo);

        final List<Alarm> alarms = alarmProcessor.getAlarms(accountId, senseId);

        return new GenericResult(Outcome.OK, Optional.absent(), Optional.of ("Ok, your alarm is canceled"));
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