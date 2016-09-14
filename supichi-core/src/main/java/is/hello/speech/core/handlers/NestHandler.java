package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hello.suripu.core.speech.interfaces.Vault;
import com.hello.suripu.coredropwizard.models.NestApplicationData;
import com.hello.suripu.coredropwizard.oauth.ExternalApplication;
import com.hello.suripu.coredropwizard.oauth.ExternalApplicationData;
import com.hello.suripu.coredropwizard.oauth.ExternalToken;
import com.hello.suripu.coredropwizard.oauth.stores.PersistentExternalAppDataStore;
import com.hello.suripu.coredropwizard.oauth.stores.PersistentExternalApplicationStore;
import com.hello.suripu.coredropwizard.oauth.stores.PersistentExternalTokenStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import is.hello.gaibu.homeauto.services.NestThermostat;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;


public class NestHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(NestHandler.class);

    private static final String TEMP_SET_PATTERN = "^.*\\s(\\w+)\\s(\\w+)\\sdegrees";
    private static final String TOGGLE_ACTIVE_PATTERN = "^.*turn.*(?:nest|thermostat)?\\s(on|off).*(?:nest|thermostat)?";

    private final SpeechCommandDAO speechCommandDAO;
    private final PersistentExternalTokenStore externalTokenStore;
    private final PersistentExternalApplicationStore externalApplicationStore;
    private final Vault tokenKMSVault;
    private ExternalApplication externalApp;
    private final PersistentExternalAppDataStore externalAppDataStore;
    private ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Integer> numberWords;

    public NestHandler(final SpeechCommandDAO speechCommandDAO,
                       final PersistentExternalTokenStore externalTokenStore,
                       final PersistentExternalApplicationStore externalApplicationStore,
                       final PersistentExternalAppDataStore externalAppDataStore,
                       final Vault tokenKMSVault) {
        super("nest_thermostat", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
        this.externalTokenStore = externalTokenStore;
        this.externalApplicationStore = externalApplicationStore;
        this.externalAppDataStore = externalAppDataStore;
        this.tokenKMSVault = tokenKMSVault;
        numberWords = Maps.newHashMap();
        init();
    }

    private void init() {
        final Optional<ExternalApplication> externalApplicationOptional = externalApplicationStore.getApplicationByName("Nest");
        if(!externalApplicationOptional.isPresent()) {
            LOGGER.error("error=application-not-found app_name=Nest");
        }
        externalApp = externalApplicationOptional.get();

        numberWords.put("one", 1);
        numberWords.put("two", 2);
        numberWords.put("three", 3);
        numberWords.put("four", 4);
        numberWords.put("five", 5);
        numberWords.put("six", 6);
        numberWords.put("seven", 7);
        numberWords.put("eight", 8);
        numberWords.put("nine", 9);
        numberWords.put("ten", 10);
        numberWords.put("twenty", 20);
        numberWords.put("thirty", 30);
        numberWords.put("forty", 40);
        numberWords.put("fifty", 50);
        numberWords.put("sixty", 60);
        numberWords.put("seventy", 70);
        numberWords.put("eighty", 80);
        numberWords.put("ninety", 90);
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("fake the", SpeechCommand.THERMOSTAT_READ);
        tempMap.put(TEMP_SET_PATTERN, SpeechCommand.THERMOSTAT_SET);
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(String text, String senseId, Long accountId) {
        final Optional<SpeechCommand> optionalCommand = getCommand(text); // TODO: ensure that only valid commands are returned
        final Map<String, String> response = Maps.newHashMap();

        String command = HandlerResult.EMPTY_COMMAND;

        if (!optionalCommand.isPresent()) {
            response.put("result", HandlerResult.Outcome.FAIL.getValue());
            return new HandlerResult(HandlerType.NEST, command, response);
        }

        if (senseId == null) {
            response.put("result", HandlerResult.Outcome.FAIL.getValue());
            return new HandlerResult(HandlerType.NEST, command, response);
        }

        final Optional<ExternalToken> externalTokenOptional = externalTokenStore.getTokenByDeviceId(senseId, externalApp.id);
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

        final Optional<ExternalApplicationData> extAppDataOptional = externalAppDataStore.getAppData(externalApp.id, senseId);
        if(!extAppDataOptional.isPresent()) {
            LOGGER.error("error=no-ext-app-data account_id={}", accountId);
            response.put("result", HandlerResult.Outcome.FAIL.getValue());
            return new HandlerResult(HandlerType.NEST, command, response);
        }

        final ExternalApplicationData extData = extAppDataOptional.get();

        NestThermostat nest;
        try {
            final NestApplicationData nestData = mapper.readValue(extData.data, NestApplicationData.class);
            nest = new NestThermostat(nestData.thermostatId, NestThermostat.DEFAULT_API_PATH, decryptedToken);

        } catch (IOException io) {
            LOGGER.warn("warn=bad-json-data");
            response.put("result", HandlerResult.Outcome.FAIL.getValue());
            return new HandlerResult(HandlerType.NEST, command, response);
        }

        command = optionalCommand.get().getValue();

        if (optionalCommand.get().equals(SpeechCommand.THERMOSTAT_SET)) {
            final Pattern r = Pattern.compile(TEMP_SET_PATTERN);
            Matcher m = r.matcher(text);
            Integer temperatureSum = 0;
            if (m.find( )) {
                if(numberWords.containsKey(m.group(1))) {
                    temperatureSum += numberWords.get(m.group(1));
                }
                if(numberWords.containsKey(m.group(2))) {
                    temperatureSum += numberWords.get(m.group(2));
                }
            } else {
                System.out.println("NO MATCH");
                response.put("result", HandlerResult.Outcome.FAIL.getValue());
                return new HandlerResult(HandlerType.NEST, command, response);
            }
            nest.setTargetTemperature(temperatureSum);
        }

        if (optionalCommand.get().equals(SpeechCommand.THERMOSTAT_ACTIVE)) {
            final Pattern r = Pattern.compile(TOGGLE_ACTIVE_PATTERN);
            Matcher m = r.matcher(text);
            Boolean isOn = false;
            if (m.find( )) {
                isOn = m.group(1).startsWith("on");

            } else {
                System.out.println("NO MATCH");
                response.put("result", HandlerResult.Outcome.FAIL.getValue());
                return new HandlerResult(HandlerType.NEST, command, response);
            }
        }

        if (text.contains("light off") | text.contains("turn off")) {
            nest.getTemperature();
        }

        response.put("result", HandlerResult.Outcome.OK.getValue());


        return new HandlerResult(HandlerType.NEST, command, response);
    }

}
