package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

import com.hello.suripu.core.speech.interfaces.Vault;
import com.hello.suripu.coredropwizard.oauth.ExternalToken;
import com.hello.suripu.coredropwizard.oauth.stores.PersistentExternalTokenStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import is.hello.gaibu.homeauto.services.HueLight;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;


/**
 * Created by ksg on 6/17/16
 */
public class HueHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HueHandler.class);


    private final SpeechCommandDAO speechCommandDAO;
    private final PersistentExternalTokenStore externalTokenStore;
    private final Vault tokenKMSVault;

    private static final String TOGGLE_ACTIVE_PATTERN = "^.*turn.*(?:light|lamp)?\\s(on|off).*(?:light|lamp)?";

    public HueHandler(final SpeechCommandDAO speechCommandDAO,
                      final PersistentExternalTokenStore externalTokenStore,
                      final Vault tokenKMSVault) {
        super("hue_light", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
        this.externalTokenStore = externalTokenStore;
        this.tokenKMSVault = tokenKMSVault;
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
    public HandlerResult executeCommand(String text, String senseId, Long accountId) {
        final Optional<SpeechCommand> optionalCommand = getCommand(text); // TODO: ensure that only valid commands are returned
        final Map<String, String> response = Maps.newHashMap();

        String command = HandlerResult.EMPTY_COMMAND;

        if (optionalCommand.isPresent()) {
            command = optionalCommand.get().getValue();

            final Optional<ExternalToken> externalTokenOptional = externalTokenStore.getTokenByDeviceId(senseId);
            if(!externalTokenOptional.isPresent()) {
                LOGGER.error("error=token-not-found device_id={}", senseId);
                response.put("result", HandlerResult.Outcome.FAIL.getValue());
                return new HandlerResult(HandlerType.NEST, command, response);
            }

            final ExternalToken externalToken = externalTokenOptional.get();

            final Map<String, String> encryptionContext = Maps.newHashMap();
            encryptionContext.put("application_id", externalToken.appId.toString());
            final Optional<String> decryptedTokenOptional = tokenKMSVault.decrypt(externalToken.accessToken, encryptionContext);

            if(!decryptedTokenOptional.isPresent()) {
                LOGGER.error("error=token-decryption-failure device_id={}", senseId);
                response.put("result", HandlerResult.Outcome.FAIL.getValue());
                return new HandlerResult(HandlerType.NEST, command, response);
            }

            final String decryptedToken = decryptedTokenOptional.get();

            final HueLight light = new HueLight("https://api.meethue.com/v2/", decryptedToken);

            if (text.contains("light on") | text.contains("turn on")) {
                light.setLightState(true);
                response.put("light", "on");
            }
            if (text.contains("light off") | text.contains("turn off")) {
                light.setLightState(false);
                response.put("light", "off");
            }
            if (text.contains("increase") | text.contains("bright")) {
                light.adjustBrightness(30);
            }
            if (text.contains("decrease") | text.contains("dim")) {
                light.adjustBrightness(-30);
            }
            if (text.contains("warmer") | text.contains("redder")) {
                light.adjustTemperature(100);
            }
            if (text.contains("cooler") | text.contains("bluer")) {
                light.adjustTemperature(-100);
            }

            response.put("result", HandlerResult.Outcome.OK.getValue());

        }

        return new HandlerResult(HandlerType.HUE, command, response);
    }

}
