package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hello.suripu.core.speech.interfaces.Vault;

import org.apache.commons.codec.binary.Base64;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import is.hello.gaibu.core.exceptions.InvalidExternalTokenException;
import is.hello.gaibu.core.models.Expansion;
import is.hello.gaibu.core.models.ExpansionData;
import is.hello.gaibu.core.models.ExternalToken;
import is.hello.gaibu.core.stores.PersistentExpansionDataStore;
import is.hello.gaibu.core.stores.PersistentExpansionStore;
import is.hello.gaibu.core.stores.PersistentExternalTokenStore;
import is.hello.gaibu.homeauto.clients.HueLight;
import is.hello.gaibu.homeauto.models.HueExpansionDeviceData;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.handlers.results.GenericResult;
import is.hello.speech.core.handlers.results.Outcome;
import is.hello.speech.core.models.AnnotatedTranscript;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;
import is.hello.speech.core.models.VoiceRequest;


/**
 * Created by ksg on 6/17/16
 */
public class HueHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HueHandler.class);

    private final String hueAppName;
    private final SpeechCommandDAO speechCommandDAO;
    private final PersistentExternalTokenStore externalTokenStore;
    private final PersistentExpansionStore externalApplicationStore;
    private final Vault tokenKMSVault;
    private Optional<Expansion> expansionOptional;
    private final PersistentExpansionDataStore externalAppDataStore;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String TOGGLE_ACTIVE_PATTERN = "(?i)^.*turn.*(?:light|lamp)?\\s(on|off).*(?:light|lamp)?";
    public static final Integer BRIGHTNESS_INCREMENT = 30;
    public static final Integer BRIGHTNESS_DECREMENT = -BRIGHTNESS_INCREMENT;
    public static final Integer COLOR_TEMPERATURE_INCREMENT = 100;
    public static final Integer COLOR_TEMPERATURE_DECREMENT = -COLOR_TEMPERATURE_INCREMENT;
    public static final String SET_LIGHT_OK_RESPONSE = "Okay, it is done.";
    public static final String SET_LIGHT_ERROR_RESPONSE = "Sorry, we're unable to adjust your lights. Please try again later";
    public static final String SET_LIGHT_ERROR_AUTH = "Sorry, we're unable to adjust your lights. Please configure Hue in the Sense app and try again.";
    public static final String SET_LIGHT_ERROR_CONFIG = "Sorry, we're unable to adjust your lights. Please select a Hue light group in the Sense app and try again.";
    public static final String SET_LIGHT_ERROR_APPLICATION = "Sorry, we're unable to adjust your lights. This expansion is currently unavailable.";

    public HueHandler(final String hueAppName,
                      final SpeechCommandDAO speechCommandDAO,
                      final PersistentExternalTokenStore externalTokenStore,
                      final PersistentExpansionStore externalApplicationStore,
                      final PersistentExpansionDataStore externalAppDataStore,
                      final Vault tokenKMSVault) {
        super("hue_light", speechCommandDAO, getAvailableActions());
        this.hueAppName = hueAppName;
        this.speechCommandDAO = speechCommandDAO;
        this.externalTokenStore = externalTokenStore;
        this.externalApplicationStore = externalApplicationStore;
        this.externalAppDataStore = externalAppDataStore;
        this.tokenKMSVault = tokenKMSVault;
        init();
    }

    private void init() {
        final Optional<Expansion> externalApplicationOptional = externalApplicationStore.getApplicationByName("Hue");
        if(!externalApplicationOptional.isPresent()) {
            LOGGER.error("error=application-not-found app_name=Hue");
        }
        expansionOptional = externalApplicationOptional;
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("brighten the", SpeechCommand.LIGHT_SET_BRIGHTNESS);
        tempMap.put("increase the", SpeechCommand.LIGHT_SET_BRIGHTNESS);
        tempMap.put("light brighter", SpeechCommand.LIGHT_SET_BRIGHTNESS);
        tempMap.put("dim the", SpeechCommand.LIGHT_SET_BRIGHTNESS);
        tempMap.put("reduce the", SpeechCommand.LIGHT_SET_BRIGHTNESS);
        tempMap.put("light dimmer", SpeechCommand.LIGHT_SET_BRIGHTNESS);
        tempMap.put("light warmer", SpeechCommand.LIGHT_SET_COLOR);
        tempMap.put("light redder", SpeechCommand.LIGHT_SET_COLOR);
        tempMap.put("light bluer", SpeechCommand.LIGHT_SET_COLOR);
        tempMap.put("light cooler", SpeechCommand.LIGHT_SET_COLOR);
        tempMap.put(TOGGLE_ACTIVE_PATTERN, SpeechCommand.LIGHT_TOGGLE);
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        final Map<String, String> response = Maps.newHashMap();

        final String senseId = request.senseId;
        final Long accountId = request.accountId;

        GenericResult hueResult;

        if(!expansionOptional.isPresent()) {
            LOGGER.error("error=application-not-found app_name=Hue");
            response.put("error", "no-application");
            response.put("result", Outcome.FAIL.getValue());
            hueResult = GenericResult.failWithResponse("expansion not found", SET_LIGHT_ERROR_APPLICATION);
            return new HandlerResult(HandlerType.HUE, HandlerResult.EMPTY_COMMAND, response, Optional.of(hueResult));
        }

        final Expansion expansion = expansionOptional.get();

        final String text = annotatedTranscript.transcript;
        final Optional<SpeechCommand> optionalCommand = getCommand(text); // TODO: ensure that only valid commands are returned


        if (!optionalCommand.isPresent()) {
            LOGGER.error("error=no-command app_name=hue text={}", text);
            response.put("error", "no-command");
            response.put("result", Outcome.FAIL.getValue());
            hueResult = GenericResult.failWithResponse("command not found", SET_LIGHT_ERROR_RESPONSE);
            return new HandlerResult(HandlerType.HUE, HandlerResult.EMPTY_COMMAND, response, Optional.of(hueResult));
        }

        final SpeechCommand command = optionalCommand.get();


        final Optional<ExternalToken> externalTokenOptional = externalTokenStore.getTokenByDeviceId(senseId, expansion.id);
        if(!externalTokenOptional.isPresent()) {
            LOGGER.error("error=token-not-found sense_id={}", senseId);
            response.put("error", "token-not-found");
            response.put("result", Outcome.FAIL.getValue());
            hueResult = GenericResult.failWithResponse("token not found", SET_LIGHT_ERROR_AUTH);
            return new HandlerResult(HandlerType.HUE, command.getValue(), response, Optional.of(hueResult));
        }

        ExternalToken externalToken = externalTokenOptional.get();

        //check for expired token and attempt refresh
        if(externalToken.hasExpired(DateTime.now(DateTimeZone.UTC))) {
            LOGGER.error("error=token-expired sense_id={}", senseId);

            final Optional<ExternalToken> refreshedTokenOptional = refreshToken(senseId, expansion, externalToken);
            if(!refreshedTokenOptional.isPresent()){
                LOGGER.error("error=token-refresh-failed sense_id={}", senseId);
                response.put("error", "token-refresh-failed");
                response.put("result", Outcome.FAIL.getValue());
                hueResult = GenericResult.failWithResponse("token refresh failed", SET_LIGHT_ERROR_AUTH);
                return new HandlerResult(HandlerType.HUE, command.getValue(), response, Optional.of(hueResult));
            }

            externalToken = refreshedTokenOptional.get();
        }

        final Map<String, String> encryptionContext = Maps.newHashMap();
        encryptionContext.put("application_id", externalToken.appId.toString());
        final Optional<String> decryptedTokenOptional = tokenKMSVault.decrypt(externalToken.accessToken, encryptionContext);


        if(!decryptedTokenOptional.isPresent()) {
            LOGGER.error("error=token-decryption-failure sense_id={}", senseId);
            response.put("error", "token-decryption-failure");
            response.put("result", Outcome.FAIL.getValue());
            hueResult = GenericResult.failWithResponse("token decrypt failed", SET_LIGHT_ERROR_AUTH);
            return new HandlerResult(HandlerType.HUE, command.getValue(), response, Optional.of(hueResult));
        }

        final String decryptedToken = decryptedTokenOptional.get();

        final Optional<ExpansionData> extAppDataOptional = externalAppDataStore.getAppData(expansion.id, senseId);
        if(!extAppDataOptional.isPresent()) {
            LOGGER.error("error=no-ext-app-data account_id={}", accountId);
            response.put("error", "no-ext-app-data");
            response.put("result", Outcome.FAIL.getValue());
            hueResult = GenericResult.failWithResponse("no expansion data", SET_LIGHT_ERROR_CONFIG);
            return new HandlerResult(HandlerType.HUE, command.getValue(), response, Optional.of(hueResult));
        }

        final ExpansionData extData = extAppDataOptional.get();
        if(extData.data.isEmpty()) {
            LOGGER.error("error=no-ext-app-data account_id={}", accountId);
            response.put("error", "no-ext-app-data");
            response.put("result", Outcome.FAIL.getValue());
            hueResult = GenericResult.failWithResponse("no expansion data", SET_LIGHT_ERROR_CONFIG);
            return new HandlerResult(HandlerType.HUE, command.getValue(), response, Optional.of(hueResult));
        }

        HueLight light;
        try {
            final HueExpansionDeviceData hueData = mapper.readValue(extData.data, HueExpansionDeviceData.class);
            light = HueLight.create(hueAppName, expansion.apiURI, decryptedToken, hueData.bridgeId, hueData.whitelistId, hueData.groupId);

        } catch (IOException io) {
            LOGGER.error("error=bad-app-data sense_id={}", senseId);
            response.put("error", "bad-app-data");
            response.put("result", Outcome.FAIL.getValue());
            hueResult = GenericResult.failWithResponse("bad expansion data", SET_LIGHT_ERROR_CONFIG);
            return new HandlerResult(HandlerType.HUE, command.getValue(), response, Optional.of(hueResult));
        }

        if (command.equals(SpeechCommand.LIGHT_TOGGLE)) {
            final Pattern r = Pattern.compile(TOGGLE_ACTIVE_PATTERN);
            final Matcher matcher = r.matcher(text);
            if (matcher.find( )) {
                final Boolean isOn = (matcher.group(1).equalsIgnoreCase("on"));
                final Boolean isSuccessful = light.setLightState(isOn);

                response.put("light_on", isOn.toString());

                if(isSuccessful) {
                    GenericResult.ok(SET_LIGHT_OK_RESPONSE);
                    response.put("result", Outcome.OK.getValue());
                    hueResult = GenericResult.ok(SET_LIGHT_OK_RESPONSE);
                    return new HandlerResult(HandlerType.HUE, command.getValue(), response, Optional.of(hueResult));
                }
            }
        }

        if (command.equals(SpeechCommand.LIGHT_SET_BRIGHTNESS)) {
            final ImmutableMap<String, Integer> adjustments = ImmutableMap.<String, Integer>builder()
                .put("increase", BRIGHTNESS_INCREMENT)
                .put("bright", BRIGHTNESS_INCREMENT)
                .put("brighter", BRIGHTNESS_INCREMENT)
                .put("decrease", BRIGHTNESS_DECREMENT)
                .put("dim", BRIGHTNESS_DECREMENT)
                .put("dimmer", BRIGHTNESS_DECREMENT)
                .build();

            for(final Map.Entry<String, Integer> adjustment : adjustments.entrySet()) {
                if(text.toLowerCase().contains(adjustment.getKey())){
                    final Boolean isSuccessful = light.adjustBrightness(adjustment.getValue());
                    response.put("brightness_adjust", adjustment.getValue().toString());

                    if(isSuccessful) {
                        response.put("result", Outcome.OK.getValue());
                        hueResult = GenericResult.ok(SET_LIGHT_OK_RESPONSE);
                        return new HandlerResult(HandlerType.HUE, command.getValue(), response, Optional.of(hueResult));
                    }
                }
            }
        }

        if (command.equals(SpeechCommand.LIGHT_SET_COLOR)) {
            final ImmutableMap<String, Integer> adjustments = ImmutableMap.<String, Integer>builder()
                .put("warmer", COLOR_TEMPERATURE_INCREMENT)
                .put("redder", COLOR_TEMPERATURE_INCREMENT)
                .put("cooler", COLOR_TEMPERATURE_DECREMENT)
                .put("bluer", COLOR_TEMPERATURE_DECREMENT)
                .build();

            for(final Map.Entry<String, Integer> adjustment : adjustments.entrySet()) {
                if(text.toLowerCase().contains(adjustment.getKey())){
                    final Boolean isSuccessful = light.adjustBrightness(adjustment.getValue());
                    response.put("color_temp_adjust", adjustment.getValue().toString());

                    if(isSuccessful) {
                        response.put("result", Outcome.OK.getValue());
                        hueResult = GenericResult.ok(SET_LIGHT_OK_RESPONSE);
                        return new HandlerResult(HandlerType.HUE, command.getValue(), response, Optional.of(hueResult));
                    }
                }
            }
        }

        response.put("result", Outcome.FAIL.getValue());
        hueResult = GenericResult.failWithResponse("unknown command", SET_LIGHT_ERROR_RESPONSE);
        return new HandlerResult(HandlerType.HUE, command.getValue(), response, Optional.of(hueResult));
    }
    @Override
    public Integer matchAnnotations(final AnnotatedTranscript annotatedTranscript) {
        // TODO HueAnnotation
        return NO_ANNOTATION_SCORE;
    }

    private Optional<ExternalToken> refreshToken(final String deviceId, final Expansion expansion, final ExternalToken externalToken) {
        final Map<String, String> encryptionContext = Maps.newHashMap();
        encryptionContext.put("application_id", externalToken.appId.toString());

        final Optional<String> decryptedRefreshTokenOptional = tokenKMSVault.decrypt(externalToken.refreshToken, encryptionContext);
        if(!decryptedRefreshTokenOptional.isPresent()) {
            LOGGER.error("error=refresh-decrypt-failed sense_id={}", deviceId);
            return Optional.absent();
        }
        final String decryptedRefreshToken = decryptedRefreshTokenOptional.get();

        //Make request to TOKEN_URL for access_token
        Client client = ClientBuilder.newClient();
        WebTarget resourceTarget = client.target(UriBuilder.fromUri(expansion.refreshURI).build());
        Invocation.Builder builder = resourceTarget.request();

        final Form form = new Form();
        form.param("refresh_token", decryptedRefreshToken);
        form.param("client_id", expansion.clientId);
        form.param("client_secret", expansion.clientSecret);

        //Hue documentation does NOT mention that this needs to be done for token refresh, but it does
        final String clientCreds = expansion.clientId + ":" + expansion.clientSecret;
        final byte[] encodedBytes = Base64.encodeBase64(clientCreds.getBytes());
        final String encodedClientCreds = new String(encodedBytes);

        Response response = builder.accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Basic " + encodedClientCreds)
            .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED));
        final String responseValue = response.readEntity(String.class);

        final Gson gson = new Gson();
        final Type collectionType = new TypeToken<Map<String, String>>(){}.getType();
        final Map<String, String> responseJson = gson.fromJson(responseValue, collectionType);

        if(!responseJson.containsKey("access_token")) {
            LOGGER.error("error=no-access-token-returned");
            return Optional.absent();
        }

        //Invalidate current token
        externalTokenStore.disableByRefreshToken(externalToken.refreshToken);

        final Optional<String> encryptedTokenOptional = tokenKMSVault.encrypt(responseJson.get("access_token"), encryptionContext);
        if (!encryptedTokenOptional.isPresent()) {
            LOGGER.error("error=access-token-encryption-failure");
            return Optional.absent();
        }

        //Store the access_token & refresh_token (if exists)
        final ExternalToken.Builder tokenBuilder = new ExternalToken.Builder()
            .withAccessToken(encryptedTokenOptional.get())
            .withAppId(expansion.id)
            .withDeviceId(deviceId);

        if(responseJson.containsKey("expires_in")) {
            tokenBuilder.withAccessExpiresIn(Long.parseLong(responseJson.get("expires_in")));
        }

        if(responseJson.containsKey("access_token_expires_in")) {
            tokenBuilder.withAccessExpiresIn(Long.parseLong(responseJson.get("access_token_expires_in")));
        }

        if(responseJson.containsKey("refresh_token")) {
            final Optional<String> encryptedRefreshTokenOptional = tokenKMSVault.encrypt(responseJson.get("refresh_token"), encryptionContext);
            if (!encryptedRefreshTokenOptional.isPresent()) {
                LOGGER.error("error=refresh-token-encryption-failure");
                return Optional.absent();
            }
            tokenBuilder.withRefreshToken(encryptedRefreshTokenOptional.get());
        }

        if(responseJson.containsKey("refresh_token_expires_in")) {
            tokenBuilder.withRefreshExpiresIn(Long.parseLong(responseJson.get("refresh_token_expires_in")));
        }

        final ExternalToken newExternalToken = tokenBuilder.build();

        //Store the externalToken
        try {
            externalTokenStore.storeToken(newExternalToken);
            return Optional.of(newExternalToken);
        } catch (InvalidExternalTokenException ie) {
            LOGGER.error("error=token-not-saved");
            return Optional.absent();
        }
    }
}
