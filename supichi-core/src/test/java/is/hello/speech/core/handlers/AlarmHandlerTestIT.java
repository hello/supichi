package is.hello.speech.core.handlers;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.DeleteTableRequest;
import com.amazonaws.services.dynamodbv2.model.ResourceInUseException;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hello.suripu.core.alarm.AlarmProcessor;
import com.hello.suripu.core.db.AlarmDAODynamoDB;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.models.Alarm;
import com.hello.suripu.core.models.AlarmSource;
import com.hello.suripu.core.models.TimeZoneHistory;
import com.hello.suripu.core.models.UserInfo;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.handlers.results.Outcome;
import is.hello.speech.core.models.AnnotatedTranscript;
import is.hello.speech.core.models.Annotator;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.VoiceRequest;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.awt.*;
import java.util.Collections;
import java.util.List;

import static is.hello.speech.core.handlers.AlarmHandler.CANCEL_ALARM_OK_RESPONSE;
import static is.hello.speech.core.handlers.AlarmHandler.DEFAULT_ALARM_SOUND;
import static is.hello.speech.core.handlers.AlarmHandler.DUPLICATE_ALARM_RESPONSE;
import static is.hello.speech.core.handlers.AlarmHandler.DUPLICATE_ERROR;
import static is.hello.speech.core.handlers.AlarmHandler.NO_ALARM_RESPONSE;
import static is.hello.speech.core.handlers.AlarmHandler.NO_TIME_ERROR;
import static is.hello.speech.core.handlers.AlarmHandler.SET_ALARM_ERROR_RESPONSE;
import static is.hello.speech.core.handlers.AlarmHandler.SET_ALARM_OK_RESPONSE;
import static is.hello.speech.core.handlers.AlarmHandler.TOO_SOON_ERROR;
import static is.hello.speech.core.handlers.ErrorText.NO_TIMEZONE;
import static is.hello.speech.core.models.SpeechCommand.ALARM_DELETE;
import static is.hello.speech.core.models.SpeechCommand.ALARM_SET;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AlarmHandlerTestIT {

    private final String tableName = "alarm_info_test";
    private static final String RACE_CONDITION_ERROR_MSG = "Cannot update alarm, please refresh and try again.";

    private final SpeechCommandDAO speechCommandDAO = mock(SpeechCommandDAO.class);
    private final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB = mock(TimeZoneHistoryDAODynamoDB.class);
    private final AlarmDAODynamoDB alarmDAO = mock(AlarmDAODynamoDB.class);

    private final String SENSE_ID = "123456789";
    private final Long ACCOUNT_ID = 99L;
    private final String FAIL_SENSE_ID = "12345678910";
    private final Long FAIL_ACCOUNT_ID = 100L;

    private final DateTimeZone TIME_ZONE = DateTimeZone.forID("America/Los_Angeles");


    private MergedUserInfoDynamoDB mergedUserInfoDynamoDB;

    private AmazonDynamoDBClient amazonDynamoDBClient;

    @Before
    public void setUp() {
        final BasicAWSCredentials awsCredentials = new BasicAWSCredentials("FAKE_AWS_KEY", "FAKE_AWS_SECRET");
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setMaxErrorRetry(0);
        this.amazonDynamoDBClient = new AmazonDynamoDBClient(awsCredentials, clientConfiguration);
        this.amazonDynamoDBClient.setEndpoint("http://localhost:7777");

        cleanUp();

        try {
            MergedUserInfoDynamoDB.createTable(tableName, this.amazonDynamoDBClient);
            this.mergedUserInfoDynamoDB = new MergedUserInfoDynamoDB(this.amazonDynamoDBClient, tableName);
        } catch (ResourceInUseException ignored) {
        }

        final int offsetMillis = TIME_ZONE.getOffset(DateTime.now(DateTimeZone.UTC).getMillis());
        final Optional<TimeZoneHistory> optionalTimeZoneHistory = Optional.of(new TimeZoneHistory(offsetMillis, "America/Los_Angeles"));
        when(timeZoneHistoryDAODynamoDB.getCurrentTimeZone(Mockito.anyLong())).thenReturn(optionalTimeZoneHistory);

        // the next day from now, 9am. smart alarm
        final DateTime existingAlarm = DateTime.now(TIME_ZONE).plusDays(1).withHourOfDay(9);
        final Alarm newAlarm = new Alarm.Builder()
                .withYear(existingAlarm.getYear())
                .withMonth(existingAlarm.getMonthOfYear())
                .withDay(existingAlarm.getDayOfMonth())
                .withHour(existingAlarm.getHourOfDay())
                .withMinute(0)
                .withDayOfWeek(Sets.newHashSet(existingAlarm.getDayOfWeek()))
                .withIsRepeated(false)
                .withAlarmSound(DEFAULT_ALARM_SOUND)
                .withIsEnabled(true)
                .withIsEditable(true)
                .withIsSmart(true)
                .withSource(AlarmSource.MOBILE_APP)
                .build();

        final List<Alarm> returnedAlarms = Lists.newArrayList();
        returnedAlarms.add(newAlarm);

        mergedUserInfoDynamoDB.setTimeZone(SENSE_ID, ACCOUNT_ID, TIME_ZONE);
        final Optional<UserInfo> userInfoOptional = mergedUserInfoDynamoDB.getInfo(SENSE_ID, ACCOUNT_ID);
        if (userInfoOptional.isPresent()) {
            final long lastUpdated = userInfoOptional.get().lastUpdatedAt;
            mergedUserInfoDynamoDB.setAlarms(SENSE_ID, ACCOUNT_ID, lastUpdated, Collections.emptyList(), returnedAlarms, TIME_ZONE);
        }
        mergedUserInfoDynamoDB.setPillColor(FAIL_SENSE_ID, FAIL_ACCOUNT_ID, "123", Color.black);
    }

    @After
    public void cleanUp() {
        final DeleteTableRequest deleteTableRequest = new DeleteTableRequest()
                .withTableName(tableName);
        try {
            this.amazonDynamoDBClient.deleteTable(deleteTableRequest);
        } catch (ResourceNotFoundException ignored) {
        }
    }


    @Test
    public void testSetAlarmOK() {
        final AlarmProcessor alarmProcessor = new AlarmProcessor(alarmDAO, mergedUserInfoDynamoDB);
        final AlarmHandler alarmHandler = new AlarmHandler(speechCommandDAO, alarmProcessor, mergedUserInfoDynamoDB);
        final String transcript = "set my alarm for 7 am";
        final AnnotatedTranscript annotatedTranscript = Annotator.get(transcript, Optional.of(TIME_ZONE.toTimeZone()));

        final HandlerResult result = alarmHandler.executeCommand(annotatedTranscript, new VoiceRequest(SENSE_ID, ACCOUNT_ID, transcript, ""));
        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.command, ALARM_SET.getValue());

        if (result.alarmResult.isPresent()) {
            assertEquals(result.alarmResult.get().outcome, Outcome.OK);
            assertEquals(result.alarmResult.get().responseText.isPresent(), true);

            final DateTime localNow = DateTime.now(TIME_ZONE);
            final int currentHour = localNow.getHourOfDay();
            final String dayString;
            if (currentHour > 7) {
                dayString = String.format(SET_ALARM_OK_RESPONSE, "07:00 AM tomorrow");
            } else {
                dayString = String.format(SET_ALARM_OK_RESPONSE, "07:00 AM today");
            }

            final String response = result.alarmResult.get().responseText.get();
            assertEquals(response, dayString);
        } else {
            assertEquals(result.alarmResult.isPresent(), true);
        }
    }

    public void testSetAlarmTodayOK() {
        final AlarmProcessor alarmProcessor = new AlarmProcessor(alarmDAO, mergedUserInfoDynamoDB);
        final AlarmHandler alarmHandler = new AlarmHandler(speechCommandDAO, alarmProcessor, mergedUserInfoDynamoDB);

        final int minutes = 30;
        final String transcript = String.format("wake me up in %d minutes", minutes);
        final AnnotatedTranscript annotatedTranscript = Annotator.get(transcript, Optional.of(TIME_ZONE.toTimeZone()));

        final HandlerResult result = alarmHandler.executeCommand(annotatedTranscript, new VoiceRequest(SENSE_ID, ACCOUNT_ID, transcript, ""));
        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.command, ALARM_SET.getValue());

        if (result.alarmResult.isPresent()) {
            assertEquals(result.alarmResult.get().outcome, Outcome.OK);
            assertEquals(result.alarmResult.get().responseText.isPresent(), true);

            final DateTime localNow = DateTime.now(TIME_ZONE);
            final DateTime alarmTime = localNow.plusMinutes(minutes);
            final String dayString;
            if (localNow.getDayOfYear() != alarmTime.getDayOfYear()) {
                dayString = "tomorrow";
            } else {
                dayString = "today";
            }

            final String response = result.alarmResult.get().responseText.get();
            assertEquals(response, dayString);
        } else {
            assertEquals(result.alarmResult.isPresent(), true);
        }
    }

    @Test
    public void testSetAlarmDuplicate() {
        final AlarmProcessor alarmProcessor = new AlarmProcessor(alarmDAO, mergedUserInfoDynamoDB);
        final AlarmHandler alarmHandler = new AlarmHandler(speechCommandDAO, alarmProcessor, mergedUserInfoDynamoDB);
        final String transcript = "set the alarm for 9 am";
        final AnnotatedTranscript annotatedTranscript = Annotator.get(transcript, Optional.of(TIME_ZONE.toTimeZone()));

        final HandlerResult result = alarmHandler.executeCommand(annotatedTranscript, new VoiceRequest(SENSE_ID, ACCOUNT_ID, transcript, ""));
        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.command, ALARM_SET.getValue());

        if (result.alarmResult.isPresent()) {
            assertEquals(result.alarmResult.get().outcome, Outcome.FAIL);
            assertEquals(result.alarmResult.get().errorText.isPresent(), true);

            final DateTime localNow = DateTime.now(TIME_ZONE);
            final int currentHour = localNow.getHourOfDay();
            final String dayString;
            if (currentHour > 7) {
                dayString = String.format(DUPLICATE_ALARM_RESPONSE, "09:00 AM tomorrow");
            } else {
                dayString = String.format(DUPLICATE_ALARM_RESPONSE, "09:00 AM today");
            }

            final String errorText = result.alarmResult.get().errorText.get();
            assertEquals(errorText, DUPLICATE_ERROR);
        } else {
            assertEquals(result.alarmResult.isPresent(), true);
        }
    }

    @Test
    public void testSetAlarmFailRaceCondition() {
        final AlarmProcessor alarmProcessor = mock(AlarmProcessor.class);
        doThrow(new RuntimeException(RACE_CONDITION_ERROR_MSG)).when(alarmProcessor).setAlarms(anyLong(), anyString(), anyListOf(Alarm.class));

        final AlarmHandler alarmHandler = new AlarmHandler(speechCommandDAO, alarmProcessor, mergedUserInfoDynamoDB);
        final String transcript = "wake me up at 8 am";
        final AnnotatedTranscript annotatedTranscript = Annotator.get(transcript, Optional.of(TIME_ZONE.toTimeZone()));

        final HandlerResult result = alarmHandler.executeCommand(annotatedTranscript, new VoiceRequest(SENSE_ID, ACCOUNT_ID, transcript, ""));
        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.command, ALARM_SET.getValue());

        if (result.alarmResult.isPresent()) {
            assertEquals(result.alarmResult.get().outcome, Outcome.FAIL);
            assertEquals(result.alarmResult.get().errorText.get(), RACE_CONDITION_ERROR_MSG);
            assertEquals(result.alarmResult.get().responseText.get(), SET_ALARM_ERROR_RESPONSE);
        } else {
            assertEquals(result.alarmResult.isPresent(), true);
        }
    }

    @Test
    public void testSetAlarmFailNoTimeZone() {
        final AlarmProcessor alarmProcessor = mock(AlarmProcessor.class);
        doThrow(new RuntimeException(NO_TIMEZONE)).when(alarmProcessor).setAlarms(anyLong(), anyString(), anyListOf(Alarm.class));

        final AlarmHandler alarmHandler = new AlarmHandler(speechCommandDAO, alarmProcessor, mergedUserInfoDynamoDB);
        final String transcript = "set an alarm for 8 am";
        final AnnotatedTranscript annotatedTranscript = Annotator.get(transcript, Optional.of(TIME_ZONE.toTimeZone()));

        final HandlerResult result = alarmHandler.executeCommand(annotatedTranscript, new VoiceRequest(FAIL_SENSE_ID, FAIL_ACCOUNT_ID, transcript, ""));
        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.command, ALARM_SET.getValue());

        if (result.alarmResult.isPresent()) {
            assertEquals(result.alarmResult.get().outcome, Outcome.FAIL);
            assertEquals(result.alarmResult.get().errorText.get(), NO_TIMEZONE);
            assertEquals(result.alarmResult.get().responseText.get(), SET_ALARM_ERROR_RESPONSE);
        } else {
            assertEquals(result.alarmResult.isPresent(), true);
        }
    }

    @Test
    public void testSetAlarmFailNoMergeInfo() {
        final AlarmProcessor alarmProcessor = new AlarmProcessor(alarmDAO, mergedUserInfoDynamoDB);
        final AlarmHandler alarmHandler = new AlarmHandler(speechCommandDAO, alarmProcessor, mergedUserInfoDynamoDB);
        final String transcript = "set alarm for 8 am";
        final AnnotatedTranscript annotatedTranscript = Annotator.get(transcript, Optional.of(TIME_ZONE.toTimeZone()));

        // apparently, only setting pill color is not enough
        final HandlerResult result = alarmHandler.executeCommand(annotatedTranscript, new VoiceRequest(FAIL_SENSE_ID, FAIL_ACCOUNT_ID, transcript, ""));
        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.command, ALARM_SET.getValue());

        if (result.alarmResult.isPresent()) {
            assertEquals(result.alarmResult.get().outcome, Outcome.FAIL);
            assertEquals(result.alarmResult.get().errorText.get(), "no merge info");
            if (result.alarmResult.get().responseText.isPresent()) {
                assertEquals(result.alarmResult.get().responseText.get(), SET_ALARM_ERROR_RESPONSE);
            }
        } else {
            assertEquals(result.alarmResult.isPresent(), true);
        }
    }

    @Test
    public void testSetAlarmFailTooSoon() {
        final AlarmProcessor alarmProcessor = new AlarmProcessor(alarmDAO, mergedUserInfoDynamoDB);
        final AlarmHandler alarmHandler = new AlarmHandler(speechCommandDAO, alarmProcessor, mergedUserInfoDynamoDB);
        final String transcript = "wake me up in 5 minutes";
        final AnnotatedTranscript annotatedTranscript = Annotator.get(transcript, Optional.of(TIME_ZONE.toTimeZone()));

        // apparently, only setting pill color is not enough
        final HandlerResult result = alarmHandler.executeCommand(annotatedTranscript, new VoiceRequest(SENSE_ID, ACCOUNT_ID, transcript, ""));
        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.command, ALARM_SET.getValue());

        if (result.alarmResult.isPresent()) {
            assertEquals(result.alarmResult.get().outcome, Outcome.FAIL);
            assertEquals(result.alarmResult.get().errorText.isPresent(), true);
            assertEquals(result.alarmResult.get().errorText.get(), TOO_SOON_ERROR);
        } else {
            assertEquals(result.alarmResult.isPresent(), true);
        }

    }

    @Test
    public void testSetAlarmFailNoTime() {
        final AlarmProcessor alarmProcessor = new AlarmProcessor(alarmDAO, mergedUserInfoDynamoDB);
        final AlarmHandler alarmHandler = new AlarmHandler(speechCommandDAO, alarmProcessor, mergedUserInfoDynamoDB);
        final String transcript = "wake me up";
        final AnnotatedTranscript annotatedTranscript = Annotator.get(transcript, Optional.of(TIME_ZONE.toTimeZone()));

        // apparently, only setting pill color is not enough
        final HandlerResult result = alarmHandler.executeCommand(annotatedTranscript, new VoiceRequest(SENSE_ID, ACCOUNT_ID, transcript, ""));
        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.command, ALARM_SET.getValue());

        if (result.alarmResult.isPresent()) {
            assertEquals(result.alarmResult.get().outcome, Outcome.FAIL);
            assertEquals(result.alarmResult.get().errorText.isPresent(), true);
            assertEquals(result.alarmResult.get().errorText.get(), NO_TIME_ERROR);
        } else {
            assertEquals(result.alarmResult.isPresent(), true);
        }

    }

    @Test
    public void testCancelAlarmOK() {
        final AlarmProcessor alarmProcessor = new AlarmProcessor(alarmDAO, mergedUserInfoDynamoDB);
        final AlarmHandler alarmHandler = new AlarmHandler(speechCommandDAO, alarmProcessor, mergedUserInfoDynamoDB);
        String transcript = "set my alarm for 7 am";
        AnnotatedTranscript annotatedTranscript = Annotator.get(transcript, Optional.of(TIME_ZONE.toTimeZone()));

        final HandlerResult result = alarmHandler.executeCommand(annotatedTranscript, new VoiceRequest(SENSE_ID, ACCOUNT_ID, transcript, ""));
        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.command, ALARM_SET.getValue());

        if (result.alarmResult.isPresent()) {
            assertEquals(result.alarmResult.get().outcome, Outcome.OK);
            assertEquals(result.alarmResult.get().responseText.isPresent(), true);
            final String responseText = result.alarmResult.get().responseText.get().toLowerCase();
            assertEquals(responseText.contains("ok, your alarm is set for 07:00 am"), true);
        } else {
            assertEquals(result.alarmResult.isPresent(), true);
        }


        // now, let's cancel the alarm that we just set
        transcript = "cancel my alarm";
        annotatedTranscript = Annotator.get(transcript, Optional.of(TIME_ZONE.toTimeZone()));

        final HandlerResult cancelResult = alarmHandler.executeCommand(annotatedTranscript, new VoiceRequest(SENSE_ID, ACCOUNT_ID, transcript, ""));
        assertEquals(cancelResult.handlerType, HandlerType.ALARM);
        assertEquals(cancelResult.command, ALARM_DELETE.getValue());

        if (result.alarmResult.isPresent()) {
            assertEquals(cancelResult.alarmResult.get().outcome, Outcome.OK);
            assertEquals(cancelResult.alarmResult.get().responseText.isPresent(), true);
            final String responseText = cancelResult.alarmResult.get().responseText.get();
            assertEquals(responseText, CANCEL_ALARM_OK_RESPONSE);
        }


        // cancel the alarm created during SetUp
        transcript = "cancel tomorrow's alarm";
        annotatedTranscript = Annotator.get(transcript, Optional.of(TIME_ZONE.toTimeZone()));

        final HandlerResult cancelResult2 = alarmHandler.executeCommand(annotatedTranscript, new VoiceRequest(SENSE_ID, ACCOUNT_ID, transcript, ""));
        assertEquals(cancelResult2.handlerType, HandlerType.ALARM);
        assertEquals(cancelResult2.command, ALARM_DELETE.getValue());
        if (result.alarmResult.isPresent()) {
            assertEquals(cancelResult2.alarmResult.get().outcome, Outcome.OK);
        }

        // try to cancel again, should fail because there's no alarm for the user
        transcript = "delete my alarm";
        annotatedTranscript = Annotator.get(transcript, Optional.of(TIME_ZONE.toTimeZone()));

        final HandlerResult cancelResult3 = alarmHandler.executeCommand(annotatedTranscript, new VoiceRequest(SENSE_ID, ACCOUNT_ID, transcript, ""));
        assertEquals(cancelResult3.handlerType, HandlerType.ALARM);
        assertEquals(cancelResult3.command, ALARM_DELETE.getValue());
        if (result.alarmResult.isPresent()) {
            assertEquals(cancelResult3.alarmResult.get().outcome, Outcome.FAIL);
            assertEquals(cancelResult3.alarmResult.get().errorText.isPresent(), true);
            final String errorText = cancelResult3.alarmResult.get().errorText.get();
            assertEquals(errorText, NO_ALARM_RESPONSE);
        }

    }

}