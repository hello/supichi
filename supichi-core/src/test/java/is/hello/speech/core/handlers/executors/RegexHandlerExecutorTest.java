package is.hello.speech.core.handlers.executors;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

import com.hello.suripu.core.speech.interfaces.Vault;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Map;

import is.hello.gaibu.core.models.ExternalApplication;
import is.hello.gaibu.core.models.ExternalApplicationData;
import is.hello.gaibu.core.models.ExternalToken;
import is.hello.gaibu.core.stores.PersistentExternalAppDataStore;
import is.hello.gaibu.core.stores.PersistentExternalApplicationStore;
import is.hello.gaibu.core.stores.PersistentExternalTokenStore;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.handlers.HueHandler;
import is.hello.speech.core.handlers.NestHandler;
import is.hello.speech.core.handlers.TriviaHandler;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;

public class RegexHandlerExecutorTest {

    final SpeechCommandDAO speechCommandDAO = mock(SpeechCommandDAO.class);
    final PersistentExternalTokenStore externalTokenStore = mock(PersistentExternalTokenStore.class);
    final PersistentExternalTokenStore badTokenStore = mock(PersistentExternalTokenStore.class);
    final PersistentExternalApplicationStore externalApplicationStore = mock(PersistentExternalApplicationStore.class);
    final PersistentExternalAppDataStore externalAppDataStore = mock(PersistentExternalAppDataStore.class);
    final Vault tokenKMSVault = mock(Vault.class);

    final String CLIENT_ID = "client_id";
    final String SENSE_ID = "123456789";
    final Long ACCOUNT_ID = 99L;

    @Before
    public void setUp() {
        final ExternalApplicationData fakeHueApplicationData = new ExternalApplicationData.Builder()
            .withAppId(1L)
            .withDeviceId(SENSE_ID)
            .withData("{\"whitelist_id\":\"123abc\", \"bridge_id\":\"fake_bridge\", \"group_id\": 1}")
            .withCreated(DateTime.now())
            .build();

        final ExternalApplicationData fakeNestApplicationData = new ExternalApplicationData.Builder()
            .withAppId(2L)
            .withDeviceId(SENSE_ID)
            .withData("{\"thermostat_id\":\"123abc\"}")
            .withCreated(DateTime.now())
            .build();

        final ExternalApplication fakeHueApplication = new ExternalApplication(1L, "Hue", CLIENT_ID, "client_secret", "http://localhost/",  "auth_uri", "token_uri", "Fake Hue Application", DateTime.now(), 2);
        final ExternalApplication fakeNestApplication = new ExternalApplication(2L, "Nest", CLIENT_ID, "client_secret", "http://localhost/",  "auth_uri", "token_uri", "Fake Nest Application", DateTime.now(), 2);


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

    }

    //Reproduce tests for UnigramHandlerExecutor to ensure regex executor doesn't break anything
    @Test
    public void TestHandleEmptyHandler() {

        final RegexHandlerExecutor executor = new RegexHandlerExecutor();
        final HandlerResult result = executor.handle("123456789", 99L, "whatever");
        assertEquals(result.handlerType, HandlerType.NONE);
    }

    @Test
    public void TestHandleSingleHandler() {

        final SpeechCommandDAO speechCommandDAO = mock(SpeechCommandDAO.class);
        final TriviaHandler handler = new TriviaHandler(speechCommandDAO);
        final HandlerExecutor executor = new RegexHandlerExecutor()
                .register(HandlerType.TRIVIA, handler);

        final HandlerResult correctResult = executor.handle("123456789", 99L, "the president");
        assertEquals(correctResult.handlerType, HandlerType.TRIVIA);

        final HandlerResult result = executor.handle("123456789", 99L, "whatever");
        assertEquals(result.handlerType, HandlerType.NONE);
    }

    //Regex specific tests
    @Test
    public void TestHueHandler() {

        final HueHandler hueHandler = new HueHandler(speechCommandDAO, externalTokenStore, externalApplicationStore, externalAppDataStore, tokenKMSVault);
        final NestHandler nestHandler = new NestHandler(speechCommandDAO, externalTokenStore, externalApplicationStore, externalAppDataStore, tokenKMSVault);

        final HandlerExecutor executor = new RegexHandlerExecutor()
            .register(HandlerType.NEST, nestHandler)
            .register(HandlerType.HUE, hueHandler);

        HandlerResult correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "turn off the light");
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_on"), "false");

        correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "turn the light off");
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_on"), "false");

        //test case insensitivity
        correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "turn the Light On");
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_on"), "true");

        correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "turn the Light Off");
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_on"), "false");

        correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "turn off the light on");
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_on"), "true");

        correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "make the light brighter");
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_brighter"), "true");

        correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "brighten the light");
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_brighter"), "true");

        correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "make the light dimmer");
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_brighter"), "false");

        correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "dim the light");
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_brighter"), "false");

        correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "make the light warmer");
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_warmer"), "true");

        correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "make the light redder");
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_warmer"), "true");

        correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "make the light cooler");
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_warmer"), "false");

        correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "make the light bluer");
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "ok");
        assertEquals(correctResult.responseParameters.get("light_warmer"), "false");


        correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "Do something random for me");
        assertNotEquals(HandlerType.HUE, correctResult.handlerType);
    }

    @Test
    public void TestNestHandler() {

        final HueHandler hueHandler = new HueHandler(speechCommandDAO, externalTokenStore, externalApplicationStore, externalAppDataStore, tokenKMSVault);
        final NestHandler nestHandler = new NestHandler(speechCommandDAO, externalTokenStore, externalApplicationStore, externalAppDataStore, tokenKMSVault);

        final HandlerExecutor executor = new RegexHandlerExecutor()
            .register(HandlerType.NEST, nestHandler)
            .register(HandlerType.HUE, hueHandler);

        HandlerResult correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "set the temp to seventy seven degrees");
        assertEquals(HandlerType.NEST, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("temp_set"), "77");

        correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "set the temp to 77 degrees");
        assertEquals(HandlerType.NEST, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("temp_set"), "77");

        correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "Do something random for me");
        assertNotEquals(HandlerType.NEST, correctResult.handlerType);
    }

    @Test
    public void TestBadToken() {
        final HueHandler hueHandler = new HueHandler(speechCommandDAO, badTokenStore, externalApplicationStore, externalAppDataStore, tokenKMSVault);
        final NestHandler nestHandler = new NestHandler(speechCommandDAO, badTokenStore, externalApplicationStore, externalAppDataStore, tokenKMSVault);


        final HandlerExecutor executor = new RegexHandlerExecutor()
            .register(HandlerType.NEST, nestHandler)
            .register(HandlerType.HUE, hueHandler);

        HandlerResult correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "turn off the light");
        assertEquals(HandlerType.HUE, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "fail");
        assertEquals(correctResult.responseParameters.get("error"), "token-not-found");

        correctResult = executor.handle(SENSE_ID, ACCOUNT_ID, "set the temp to seventy seven degrees");
        assertEquals(HandlerType.NEST, correctResult.handlerType);
        assertEquals(correctResult.responseParameters.get("result"), "fail");
        assertEquals(correctResult.responseParameters.get("error"), "token-not-found");
    }
}
