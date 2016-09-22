package is.hello.speech.core.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.hello.suripu.core.speech.interfaces.Vault;
import is.hello.gaibu.core.models.ExternalApplication;
import is.hello.gaibu.core.models.ExternalApplicationData;
import is.hello.gaibu.core.models.ExternalToken;
import is.hello.gaibu.core.models.HueApplicationData;
import is.hello.gaibu.core.stores.PersistentExternalAppDataStore;
import is.hello.gaibu.core.stores.PersistentExternalApplicationStore;
import is.hello.gaibu.core.stores.PersistentExternalTokenStore;
import is.hello.gaibu.homeauto.services.HueLight;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.handlers.results.Outcome;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;
import is.hello.speech.core.models.AnnotatedTranscript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;


/**
 * Created by ksg on 6/17/16
 */
public class HueHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HueHandler.class);


    private final SpeechCommandDAO speechCommandDAO;
    private final PersistentExternalTokenStore externalTokenStore;
    private final PersistentExternalApplicationStore externalApplicationStore;
    private final Vault tokenKMSVault;
    private ExternalApplication externalApp;
    private final PersistentExternalAppDataStore externalAppDataStore;
    private ObjectMapper mapper = new ObjectMapper();

    private static final String TOGGLE_ACTIVE_PATTERN = "^.*turn.*(?:light|lamp)?\\s(on|off).*(?:light|lamp)?";
    private static final Integer BRIGHTNESS_INCREMENT = 30;
    private static final Integer COLOR_TEMPERATURE_INCREMENT = 100;

    public HueHandler(final SpeechCommandDAO speechCommandDAO,
                      final PersistentExternalTokenStore externalTokenStore,
                      final PersistentExternalApplicationStore externalApplicationStore,
                      final PersistentExternalAppDataStore externalAppDataStore,
                      final Vault tokenKMSVault) {
        super("hue_light", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
        this.externalTokenStore = externalTokenStore;
        this.externalApplicationStore = externalApplicationStore;
        this.externalAppDataStore = externalAppDataStore;
        this.tokenKMSVault = tokenKMSVault;
        init();
    }

    private void init() {
        final Optional<ExternalApplication> externalApplicationOptional = externalApplicationStore.getApplicationByName("Hue");
        if(!externalApplicationOptional.isPresent()) {
            LOGGER.error("error=application-not-found app_name=Hue");
        }
        externalApp = externalApplicationOptional.get();
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("brighten the", SpeechCommand.LIGHT_SET);
        tempMap.put("increase the", SpeechCommand.LIGHT_SET);
        tempMap.put("light brighter", SpeechCommand.LIGHT_SET);
        tempMap.put("dim the", SpeechCommand.LIGHT_SET);
        tempMap.put("reduce the", SpeechCommand.LIGHT_SET);
        tempMap.put("light dimmer", SpeechCommand.LIGHT_SET);
        tempMap.put("light warmer", SpeechCommand.LIGHT_SET);
        tempMap.put("light redder", SpeechCommand.LIGHT_SET);
        tempMap.put("light bluer", SpeechCommand.LIGHT_SET);
        tempMap.put("light cooler", SpeechCommand.LIGHT_SET);
        tempMap.put(TOGGLE_ACTIVE_PATTERN, SpeechCommand.LIGHT_TOGGLE);
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final String senseId, final Long accountId) {
        final String text = annotatedTranscript.transcript;
        final Optional<SpeechCommand> optionalCommand = getCommand(text); // TODO: ensure that only valid commands are returned
        final Map<String, String> response = Maps.newHashMap();

        String command = HandlerResult.EMPTY_COMMAND;

        if (optionalCommand.isPresent()) {
            command = optionalCommand.get().getValue();

            final Optional<ExternalToken> externalTokenOptional = externalTokenStore.getTokenByDeviceId(senseId, externalApp.id);
            if(!externalTokenOptional.isPresent()) {
                LOGGER.error("error=token-not-found device_id={}", senseId);
                response.put("error", "token-not-found");
                response.put("result", Outcome.FAIL.getValue());
                return new HandlerResult(HandlerType.NEST, command, response, Optional.absent());
            }

            final ExternalToken externalToken = externalTokenOptional.get();

            final Map<String, String> encryptionContext = Maps.newHashMap();
            encryptionContext.put("application_id", externalToken.appId.toString());
            final Optional<String> decryptedTokenOptional = tokenKMSVault.decrypt(externalToken.accessToken, encryptionContext);

            if(!decryptedTokenOptional.isPresent()) {
                LOGGER.error("error=token-decryption-failure device_id={}", senseId);
                response.put("error", "token-decryption-failure");
                response.put("result", Outcome.FAIL.getValue());
                return new HandlerResult(HandlerType.NEST, command, response, Optional.absent());
            }

            final String decryptedToken = decryptedTokenOptional.get();

            final Optional<ExternalApplicationData> extAppDataOptional = externalAppDataStore.getAppData(externalApp.id, senseId);
            if(!extAppDataOptional.isPresent()) {
                LOGGER.error("error=no-ext-app-data account_id={}", accountId);
                response.put("error", "no-ext-app-data");
                response.put("result", Outcome.FAIL.getValue());
                return new HandlerResult(HandlerType.NEST, command, response, Optional.absent());
            }

            final ExternalApplicationData extData = extAppDataOptional.get();

            HueLight light;
            try {
                final HueApplicationData hueData = mapper.readValue(extData.data, HueApplicationData.class);
                light = new HueLight(HueLight.DEFAULT_API_PATH, decryptedToken, hueData.bridgeId, hueData.whitelistId, hueData.groupId);

            } catch (IOException io) {
                LOGGER.error("error=bad-app-data device_id={}", senseId);
                response.put("error", "bad-app-data");
                response.put("result", Outcome.FAIL.getValue());
                return new HandlerResult(HandlerType.NEST, command, response, Optional.absent());
            }


            if (text.contains("light on") | text.contains("turn on")) {
                light.setLightState(true);
                response.put("light", "on");
            }
            if (text.contains("light off") | text.contains("turn off")) {
                light.setLightState(false);
                response.put("light", "off");
            }
            if (text.contains("increase") | text.contains("bright")) {
                light.adjustBrightness(BRIGHTNESS_INCREMENT);
            }
            if (text.contains("decrease") | text.contains("dim")) {
                light.adjustBrightness(-BRIGHTNESS_INCREMENT);
            }
            if (text.contains("warmer") | text.contains("redder")) {
                light.adjustTemperature(COLOR_TEMPERATURE_INCREMENT);
            }
            if (text.contains("cooler") | text.contains("bluer")) {
                light.adjustTemperature(-COLOR_TEMPERATURE_INCREMENT);
            }

            response.put("result", Outcome.OK.getValue());

        }

        return new HandlerResult(HandlerType.HUE, command, response, Optional.absent());
    }

    @Override
    public Integer matchAnnotations(final AnnotatedTranscript annotatedTranscript) {
        // TODO HueAnnotation
        return NO_ANNOTATION_SCORE;
    }

}
