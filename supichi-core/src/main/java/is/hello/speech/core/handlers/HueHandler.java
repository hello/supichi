package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hello.suripu.core.speech.interfaces.Vault;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import is.hello.gaibu.core.models.Expansion;
import is.hello.gaibu.core.models.ExpansionData;
import is.hello.gaibu.core.models.ExternalToken;
import is.hello.gaibu.core.stores.PersistentExpansionDataStore;
import is.hello.gaibu.core.stores.PersistentExpansionStore;
import is.hello.gaibu.core.stores.PersistentExternalTokenStore;
import is.hello.gaibu.homeauto.clients.HueLight;
import is.hello.gaibu.homeauto.models.HueExpansionDeviceData;
import is.hello.speech.core.db.SpeechCommandDAO;
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

        if(!expansionOptional.isPresent()) {
            LOGGER.error("error=application-not-found app_name=Hue");
            response.put("error", "no-application");
            response.put("result", Outcome.FAIL.getValue());
            return new HandlerResult(HandlerType.HUE, HandlerResult.EMPTY_COMMAND, response, Optional.absent());
        }

        final Expansion expansion = expansionOptional.get();

        final String text = annotatedTranscript.transcript;
        final Optional<SpeechCommand> optionalCommand = getCommand(text); // TODO: ensure that only valid commands are returned


        if (!optionalCommand.isPresent()) {
            LOGGER.error("error=no-command app_name=hue text={}", text);
            response.put("error", "no-command");
            response.put("result", Outcome.FAIL.getValue());
            return new HandlerResult(HandlerType.HUE, HandlerResult.EMPTY_COMMAND, response, Optional.absent());
        }

        final SpeechCommand command = optionalCommand.get();


        final Optional<ExternalToken> externalTokenOptional = externalTokenStore.getTokenByDeviceId(senseId, expansion.id);
        if(!externalTokenOptional.isPresent()) {
            LOGGER.error("error=token-not-found device_id={}", senseId);
            response.put("error", "token-not-found");
            response.put("result", Outcome.FAIL.getValue());
            return new HandlerResult(HandlerType.HUE, HandlerResult.EMPTY_COMMAND, response, Optional.absent());
        }

        final ExternalToken externalToken = externalTokenOptional.get();

        final Map<String, String> encryptionContext = Maps.newHashMap();
        encryptionContext.put("application_id", externalToken.appId.toString());
        final Optional<String> decryptedTokenOptional = tokenKMSVault.decrypt(externalToken.accessToken, encryptionContext);


        if(!decryptedTokenOptional.isPresent()) {
            LOGGER.error("error=token-decryption-failure device_id={}", senseId);
            response.put("error", "token-decryption-failure");
            response.put("result", Outcome.FAIL.getValue());
            return new HandlerResult(HandlerType.HUE, HandlerResult.EMPTY_COMMAND, response, Optional.absent());
        }

        final String decryptedToken = decryptedTokenOptional.get();

        final Optional<ExpansionData> extAppDataOptional = externalAppDataStore.getAppData(expansion.id, senseId);
        if(!extAppDataOptional.isPresent()) {
            LOGGER.error("error=no-ext-app-data account_id={}", accountId);
            response.put("error", "no-ext-app-data");
            response.put("result", Outcome.FAIL.getValue());
            return new HandlerResult(HandlerType.HUE, HandlerResult.EMPTY_COMMAND, response, Optional.absent());
        }

        final ExpansionData extData = extAppDataOptional.get();
        if(extData.data.isEmpty()) {
            LOGGER.error("error=no-ext-app-data account_id={}", accountId);
            response.put("error", "no-ext-app-data");
            response.put("result", Outcome.FAIL.getValue());
            return new HandlerResult(HandlerType.HUE, HandlerResult.EMPTY_COMMAND, response, Optional.absent());
        }

        HueLight light;
        try {
            final HueExpansionDeviceData hueData = mapper.readValue(extData.data, HueExpansionDeviceData.class);
            light = HueLight.create(hueAppName, expansion.apiURI, decryptedToken, hueData.bridgeId, hueData.whitelistId, hueData.groupId);

        } catch (IOException io) {
            LOGGER.error("error=bad-app-data device_id={}", senseId);
            response.put("error", "bad-app-data");
            response.put("result", Outcome.FAIL.getValue());
            return new HandlerResult(HandlerType.HUE, HandlerResult.EMPTY_COMMAND, response, Optional.absent());
        }

        if (command.equals(SpeechCommand.LIGHT_TOGGLE)) {
            final Pattern r = Pattern.compile(TOGGLE_ACTIVE_PATTERN);
            final Matcher matcher = r.matcher(text);
            if (matcher.find( )) {
                final Boolean isOn = (matcher.group(1).equalsIgnoreCase("on"));
                light.setLightState(isOn);

                response.put("light_on", isOn.toString());
                response.put("result", Outcome.OK.getValue());
                return new HandlerResult(HandlerType.HUE, command.getValue(), response, Optional.absent());
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
                    light.adjustBrightness(adjustment.getValue());
                    response.put("brightness_adjust", adjustment.getValue().toString());
                    response.put("result", Outcome.OK.getValue());
                    return new HandlerResult(HandlerType.HUE, command.getValue(), response, Optional.absent());
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
                    light.adjustBrightness(adjustment.getValue());
                    response.put("color_temp_adjust", adjustment.getValue().toString());
                    response.put("result", Outcome.OK.getValue());
                    return new HandlerResult(HandlerType.HUE, command.getValue(), response, Optional.absent());
                }
            }
        }

        response.put("result", Outcome.FAIL.getValue());
        return new HandlerResult(HandlerType.HUE, command.getValue(), response, Optional.absent());
    }
    @Override
    public Integer matchAnnotations(final AnnotatedTranscript annotatedTranscript) {
        // TODO HueAnnotation
        return NO_ANNOTATION_SCORE;
    }
}
