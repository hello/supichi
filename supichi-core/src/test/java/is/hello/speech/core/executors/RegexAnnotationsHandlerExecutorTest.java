package is.hello.speech.core.executors;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.AccountLocationDAO;
import com.hello.suripu.core.db.AlarmDAODynamoDB;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.db.colors.SenseColorDAO;
import com.hello.suripu.core.models.TimeZoneHistory;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.core.speech.interfaces.Vault;
import com.hello.suripu.coredropwizard.clients.MessejiClient;
import is.hello.gaibu.core.models.Expansion;
import is.hello.gaibu.core.models.ExpansionData;
import is.hello.gaibu.core.models.ExternalToken;
import is.hello.gaibu.core.models.MultiDensityImage;
import is.hello.gaibu.core.stores.PersistentExpansionDataStore;
import is.hello.gaibu.core.stores.PersistentExpansionStore;
import is.hello.gaibu.core.stores.PersistentExternalTokenStore;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.handlers.HandlerFactory;
import is.hello.speech.core.handlers.HueHandler;
import is.hello.speech.core.handlers.NestHandler;
import is.hello.speech.core.models.VoiceRequest;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Map;

import static is.hello.speech.core.models.SpeechCommand.ALARM_DELETE;
import static is.hello.speech.core.models.SpeechCommand.ALARM_SET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;

public class RegexAnnotationsHandlerExecutorTest {
    private final SpeechCommandDAO speechCommandDAO = mock(SpeechCommandDAO.class);
    private final PersistentExternalTokenStore externalTokenStore = mock(PersistentExternalTokenStore.class);
    private final PersistentExternalTokenStore badTokenStore = mock(PersistentExternalTokenStore.class);
    private final PersistentExpansionStore externalApplicationStore = mock(PersistentExpansionStore.class);
    private final PersistentExpansionDataStore externalAppDataStore = mock(PersistentExpansionDataStore.class);
    private final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB = mock(TimeZoneHistoryDAODynamoDB.class);
    private final Vault tokenKMSVault = mock(Vault.class);
    private final AlarmDAODynamoDB alarmDAO = mock(AlarmDAODynamoDB.class);
    private final MergedUserInfoDynamoDB mergedUserDAO = mock(MergedUserInfoDynamoDB.class);

    private final MessejiClient messejiClient = mock(MessejiClient.class);
    private final SleepSoundsProcessor sleepSoundsProcessor = mock(SleepSoundsProcessor.class);
    private final DeviceDataDAODynamoDB deviceDataDAODynamoDB = mock(DeviceDataDAODynamoDB.class);
    private final DeviceDAO deviceDAO = mock(DeviceDAO.class);
    private final SenseColorDAO senseColorDAO = mock(SenseColorDAO.class);
    private final CalibrationDAO calibrationDAO = mock(CalibrationDAO.class);
    private final AccountLocationDAO accountLocationDAO = mock(AccountLocationDAO.class);

    private final String SENSE_ID = "123456789";
    private final Long ACCOUNT_ID = 99L;
    private final DateTimeZone TIME_ZONE = DateTimeZone.forID("America/Los_Angeles");


    @Before
    public void setUp() {
        final ExpansionData fakeHueApplicationData = new ExpansionData.Builder()
            .withAppId(1L)
            .withDeviceId(SENSE_ID)
            .withData("{\"whitelist_id\":\"123abc\", \"bridge_id\":\"fake_bridge\", \"group_id\": 1}")
            .withCreated(DateTime.now())
            .withEnabled(true)
            .build();

        final ExpansionData fakeNestApplicationData = new ExpansionData.Builder()
            .withAppId(2L)
            .withDeviceId(SENSE_ID)
            .withData("{\"thermostat_id\":\"123abc\"}")
            .withCreated(DateTime.now())
            .withEnabled(true)
            .build();

        String CLIENT_ID = "client_id";
        final MultiDensityImage icon = new MultiDensityImage("icon@1x.png", "icon@2x.png", "icon@3x.png");

        final Expansion fakeHueApplication = new Expansion(1L, Expansion.ServiceName.HUE,
            "Hue Light", "Fake Hue Application", icon, CLIENT_ID, "client_secret",
            "http://localhost/",  "auth_uri", "token_uri", "refresh_uri", Expansion.Category.LIGHT,
            DateTime.now(), 2, "completion_uri", Expansion.State.NOT_CONNECTED);

        final Expansion fakeNestApplication = new Expansion(2L, Expansion.ServiceName.NEST,
            "Nest Thermostat", "Fake Nest Application", icon, CLIENT_ID, "client_secret",
            "http://localhost/",  "auth_uri", "token_uri", "refresh_uri", Expansion.Category.TEMPERATURE,
            DateTime.now(), 2, "completion_uri", Expansion.State.NOT_CONNECTED);

        final ExternalToken fakeToken = new ExternalToken.Builder()
            .withAccessToken("fake_token")
            .withRefreshToken("fake_refresh")
            .withAccessExpiresIn(123456789L)
            .withRefreshExpiresIn(123456789L)
            .withDeviceId(SENSE_ID)
            .withAppId(1L)
            .build();
        final Map<String, String> encryptionContext = Maps.newHashMap();
        encryptionContext.put("application_id", fakeToken.appId.toString());

        Mockito.when(externalApplicationStore.getApplicationByName("Hue")).thenReturn(Optional.of(fakeHueApplication));
        Mockito.when(externalApplicationStore.getApplicationByName("Nest")).thenReturn(Optional.of(fakeNestApplication));
        Mockito.when(externalTokenStore.getTokenByDeviceId(Mockito.anyString(), Mockito.anyLong())).thenReturn(Optional.of(fakeToken));
        Mockito.when(badTokenStore.getTokenByDeviceId(Mockito.anyString(), Mockito.anyLong())).thenReturn(Optional.absent());
        Mockito.when(tokenKMSVault.decrypt(fakeToken.accessToken, encryptionContext)).thenReturn(Optional.of(fakeToken.accessToken));
        Mockito.when(externalAppDataStore.getAppData(1L, SENSE_ID)).thenReturn(Optional.of(fakeHueApplicationData));
        Mockito.when(externalAppDataStore.getAppData(2L, SENSE_ID)).thenReturn(Optional.of(fakeNestApplicationData));

        final int offsetMillis = TIME_ZONE.getOffset(DateTime.now(DateTimeZone.UTC).getMillis());
        final Optional<TimeZoneHistory> optionalTimeZoneHistory = Optional.of(new TimeZoneHistory(offsetMillis, "America/Los_Angeles"));
        Mockito.when(timeZoneHistoryDAODynamoDB.getCurrentTimeZone(Mockito.anyLong())).thenReturn(optionalTimeZoneHistory);

        Mockito.when(mergedUserDAO.getInfo(SENSE_ID, ACCOUNT_ID)).thenReturn(Optional.absent());

    }

    private HandlerExecutor getExecutor() {
        final HandlerFactory handlerFactory = HandlerFactory.create(
                speechCommandDAO,
                messejiClient,
                sleepSoundsProcessor,
                deviceDataDAODynamoDB,
                deviceDAO,
                senseColorDAO,
                calibrationDAO,
                timeZoneHistoryDAODynamoDB,
                "BLAH", // forecastio
                accountLocationDAO,
                externalTokenStore,
                externalApplicationStore,
                externalAppDataStore,
                tokenKMSVault,
                alarmDAO,
                mergedUserDAO
        );

        return new RegexAnnotationsHandlerExecutor(timeZoneHistoryDAODynamoDB)
                .register(HandlerType.ALARM, handlerFactory.alarmHandler())
                .register(HandlerType.SLEEP_SOUNDS, handlerFactory.sleepSoundHandler())
                .register(HandlerType.ROOM_CONDITIONS, handlerFactory.roomConditionsHandler())
                .register(HandlerType.TIME_REPORT, handlerFactory.timeHandler())
                .register(HandlerType.TRIVIA, handlerFactory.triviaHandler())
                .register(HandlerType.TIMELINE, handlerFactory.timelineHandler())
                .register(HandlerType.HUE, handlerFactory.hueHandler())
                .register(HandlerType.NEST, handlerFactory.nestHandler());
    }

    private VoiceRequest newVoiceRequest(final String transcript) {
        return new VoiceRequest(SENSE_ID, ACCOUNT_ID, transcript, "127.0.0.1");
    }

    @Test
    public void TestAlarmHandlers() {
        // test handler mapping
        final HandlerExecutor handlerExecutor = getExecutor();
        HandlerResult result = handlerExecutor.handle(newVoiceRequest("set my alarm for 7 am"));
        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.alarmResult.isPresent(), true);
        assertEquals(result.command, ALARM_SET.getValue());

        result = handlerExecutor.handle(newVoiceRequest("wake me at 7 am"));

        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.alarmResult.isPresent(), true);
        assertEquals(result.command, ALARM_SET.getValue());

        result = handlerExecutor.handle(newVoiceRequest("wake her up at 7 am"));
        assertEquals(result.handlerType, HandlerType.NONE);

        result = handlerExecutor.handle(newVoiceRequest("alarm my dentist"));
        assertEquals(result.handlerType, HandlerType.NONE);

        // cancel alarm
        result = handlerExecutor.handle(newVoiceRequest("cancel my alarm"));
        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.alarmResult.isPresent(), true);
        assertEquals(result.command, ALARM_DELETE.getValue());

        result = handlerExecutor.handle(newVoiceRequest("delete tomorrow's alarm"));
        assertEquals(result.handlerType, HandlerType.ALARM);
        assertEquals(result.alarmResult.isPresent(), true);
        assertEquals(result.command, ALARM_DELETE.getValue());

        result = handlerExecutor.handle(newVoiceRequest("cancel all my appointments"));
        assertEquals(result.handlerType, HandlerType.NONE);
        assertEquals(result.alarmResult.isPresent(), false);
    }


    //Reproduce tests for UnigramHandlerExecutor to ensure regex executor doesn't break anything
    @Test
    public void TestHandleEmptyHandler() {
        final HandlerExecutor handlerExecutor = getExecutor();

        final HandlerResult result = handlerExecutor.handle(new VoiceRequest("123456789", 99L, "whatever", ""));
        assertEquals(result.handlerType, HandlerType.NONE);
    }

    @Test
    public void TestHandleSingleHandler() {
        final HandlerExecutor executor = getExecutor();

        final HandlerResult correctResult = executor.handle(new VoiceRequest("123456789", 99L, "the president", ""));
        assertEquals(correctResult.handlerType, HandlerType.TRIVIA);

        final HandlerResult result = executor.handle(new VoiceRequest("123456789", 99L, "whatever", ""));
        assertEquals(result.handlerType, HandlerType.NONE);
    }

    //Regex specific tests
    @Test
    public void TestHueHandler() {

        final HandlerExecutor executor = getExecutor();

        HandlerResult correctResult = executor.handle(newVoiceRequest("turn off the light"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_on"), "false");

        correctResult = executor.handle(newVoiceRequest("turn the light off"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_on"), "false");

        //test case insensitivity
        correctResult = executor.handle(newVoiceRequest("turn the Light On"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_on"), "true");

        correctResult = executor.handle(newVoiceRequest("turn the Light Off"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_on"), "false");

        correctResult = executor.handle(newVoiceRequest("turn off the light on"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_on"), "true");

        correctResult = executor.handle(newVoiceRequest("make the light brighter"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("brightness_adjust"), HueHandler.BRIGHTNESS_INCREMENT.toString());

        correctResult = executor.handle(newVoiceRequest("brighten the light"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("brightness_adjust"), HueHandler.BRIGHTNESS_INCREMENT.toString());

        correctResult = executor.handle(newVoiceRequest("make the light dimmer"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("brightness_adjust"), HueHandler.BRIGHTNESS_DECREMENT.toString());

        correctResult = executor.handle(newVoiceRequest("dim the light"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("brightness_adjust"), HueHandler.BRIGHTNESS_DECREMENT.toString());

        correctResult = executor.handle(newVoiceRequest("make the light warmer"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("color_temp_adjust"), HueHandler.COLOR_TEMPERATURE_INCREMENT.toString());

        correctResult = executor.handle(newVoiceRequest("make the light redder"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("color_temp_adjust"), HueHandler.COLOR_TEMPERATURE_INCREMENT.toString());

        correctResult = executor.handle(newVoiceRequest("make the light cooler"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("color_temp_adjust"), HueHandler.COLOR_TEMPERATURE_DECREMENT.toString());

        correctResult = executor.handle(newVoiceRequest("make the light bluer"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("color_temp_adjust"), HueHandler.COLOR_TEMPERATURE_DECREMENT.toString());


        correctResult = executor.handle(newVoiceRequest("Do something random for me"));
        assertNotEquals(HandlerType.HUE, correctResult.handlerType);
    }

    @Test
    public void TestNestHandler() {

        final HandlerExecutor executor = getExecutor();

        HandlerResult correctResult = executor.handle(newVoiceRequest("set the temp to seventy seven degrees"));
        assertEquals(HandlerType.NEST, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("temp_set"), "77");

        correctResult = executor.handle(newVoiceRequest("set the temp to 77 degrees"));
        assertEquals(HandlerType.NEST, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("temp_set"), "77");

        correctResult = executor.handle(newVoiceRequest("Do something random for me"));
        assertNotEquals(HandlerType.NEST, correctResult.handlerType);
    }

    @Test
    public void TestBadToken() {
        final HueHandler hueHandler = new HueHandler(speechCommandDAO, badTokenStore, externalApplicationStore, externalAppDataStore, tokenKMSVault);
        final NestHandler nestHandler = new NestHandler(speechCommandDAO, badTokenStore, externalApplicationStore, externalAppDataStore, tokenKMSVault);

        final HandlerExecutor executor = new RegexAnnotationsHandlerExecutor(timeZoneHistoryDAODynamoDB)
            .register(HandlerType.NEST, nestHandler)
            .register(HandlerType.HUE, hueHandler);

        HandlerResult correctResult = executor.handle(newVoiceRequest("turn off the light"));
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "fail");
        assertEquals(correctResult.responseParameters.get("error"), "token-not-found");

        correctResult = executor.handle(newVoiceRequest("set the temp to seventy seven degrees"));
        assertEquals(HandlerType.NEST, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "fail");
        assertEquals(correctResult.responseParameters.get("error"), "token-not-found");
    }
}
