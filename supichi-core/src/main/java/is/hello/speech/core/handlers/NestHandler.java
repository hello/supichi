package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hello.suripu.core.speech.interfaces.Vault;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import is.hello.gaibu.core.models.ExternalApplication;
import is.hello.gaibu.core.models.ExternalApplicationData;
import is.hello.gaibu.core.models.ExternalToken;
import is.hello.gaibu.core.models.NestApplicationData;
import is.hello.gaibu.core.stores.PersistentExternalAppDataStore;
import is.hello.gaibu.core.stores.PersistentExternalApplicationStore;
import is.hello.gaibu.core.stores.PersistentExternalTokenStore;
import is.hello.gaibu.homeauto.services.NestThermostat;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.handlers.results.Outcome;
import is.hello.speech.core.models.AnnotatedTranscript;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;


public class NestHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(NestHandler.class);

    private static final String TEMP_SET_PATTERN_WORDS = "(?i)^.*(?:nest|thermostat|temp)?\\sto\\s(\\w+)\\s(\\w+)\\sdegrees";
    private static final String TEMP_SET_PATTERN_NUMERIC = "(?i)^.*(?:nest|thermostat|temp)?\\sto\\s(\\d+)\\sdegrees";
    private static final String TOGGLE_ACTIVE_PATTERN = "(?i)^.*turn.*(?:nest|thermostat)?\\s(on|off).*(?:nest|thermostat)?";

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
        tempMap.put(TEMP_SET_PATTERN_WORDS, SpeechCommand.THERMOSTAT_SET);
        tempMap.put(TEMP_SET_PATTERN_NUMERIC, SpeechCommand.THERMOSTAT_SET);
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final String senseId, final Long accountId) {
        final String text = annotatedTranscript.transcript;

        final Optional<SpeechCommand> optionalCommand = getCommand(text); // TODO: ensure that only valid commands are returned
        final Map<String, String> response = Maps.newHashMap();

        if (!optionalCommand.isPresent()) {
            LOGGER.error("error=no-command app_name=nest text={}", text);
            response.put("error", "no-command");
            response.put("result", Outcome.FAIL.getValue());
            return new HandlerResult(HandlerType.NEST, HandlerResult.EMPTY_COMMAND, response, Optional.absent());
        }

        final SpeechCommand command = optionalCommand.get();

        if (senseId == null) {
            LOGGER.error("error=null-sense-id app_name=nest");
            response.put("error", "null-sense-id");
            response.put("result", Outcome.FAIL.getValue());
            return new HandlerResult(HandlerType.NEST, HandlerResult.EMPTY_COMMAND, response, Optional.absent());
        }

        final Optional<ExternalToken> externalTokenOptional = externalTokenStore.getTokenByDeviceId(senseId, externalApp.id);
        if(!externalTokenOptional.isPresent()) {
            LOGGER.error("error=token-not-found device_id={}", senseId);
            response.put("error", "token-not-found");
            response.put("result", Outcome.FAIL.getValue());
            return new HandlerResult(HandlerType.NEST, command.getValue(), response, Optional.absent());
        }

        final ExternalToken externalToken = externalTokenOptional.get();

        final Map<String, String> encryptionContext = Maps.newHashMap();
        encryptionContext.put("application_id", externalToken.appId.toString());
        final Optional<String> decryptedTokenOptional = tokenKMSVault.decrypt(externalToken.accessToken, encryptionContext);

        if(!decryptedTokenOptional.isPresent()) {
            LOGGER.error("error=token-decryption-failure device_id={}", senseId);
            response.put("error", "token-decryption-failure");
            response.put("result", Outcome.FAIL.getValue());
            return new HandlerResult(HandlerType.NEST, command.getValue(), response, Optional.absent());
        }

        final String decryptedToken = decryptedTokenOptional.get();

        final Optional<ExternalApplicationData> extAppDataOptional = externalAppDataStore.getAppData(externalApp.id, senseId);
        if(!extAppDataOptional.isPresent()) {
            LOGGER.error("error=no-ext-app-data account_id={}", accountId);
            response.put("error", "no-ext-app-data");
            response.put("result", Outcome.FAIL.getValue());
            return new HandlerResult(HandlerType.NEST, command.getValue(), response, Optional.absent());
        }

        final ExternalApplicationData extData = extAppDataOptional.get();

        NestThermostat nest;
        try {
            final NestApplicationData nestData = mapper.readValue(extData.data, NestApplicationData.class);
            nest = new NestThermostat(nestData.thermostatId, externalApp.apiURI, decryptedToken);

        } catch (IOException io) {
            LOGGER.warn("error=bad-app-data app_name=nest device_id={}", senseId);
            response.put("error", "bad-app-data");
            response.put("result", Outcome.FAIL.getValue());
            return new HandlerResult(HandlerType.NEST, command.getValue(), response, Optional.absent());
        }

        if (command.equals(SpeechCommand.THERMOSTAT_SET)) {
            Integer temperatureSum = 0;

            final Pattern words = Pattern.compile(TEMP_SET_PATTERN_WORDS);
            Matcher m = words.matcher(text);
            if (m.find( )) {
                if(numberWords.containsKey(m.group(1))) {
                    temperatureSum += numberWords.get(m.group(1));
                }
                if(numberWords.containsKey(m.group(2))) {
                    temperatureSum += numberWords.get(m.group(2));
                }
                nest.setTargetTemperature(temperatureSum);
                response.put("temp_set", temperatureSum.toString());
                response.put("result", Outcome.OK.getValue());
                return new HandlerResult(HandlerType.NEST, command.getValue(), response, Optional.absent());
            }

            final Pattern numeric = Pattern.compile(TEMP_SET_PATTERN_NUMERIC);
            m = numeric.matcher(text);
            if (m.find( )) {
                temperatureSum += Integer.parseInt(m.group(1));
                nest.setTargetTemperature(temperatureSum);
                response.put("temp_set", temperatureSum.toString());
                response.put("result", Outcome.OK.getValue());
                return new HandlerResult(HandlerType.NEST, command.getValue(), response, Optional.absent());
            }
        }

        if (optionalCommand.get().equals(SpeechCommand.THERMOSTAT_ACTIVE)) {
            final Pattern r = Pattern.compile(TOGGLE_ACTIVE_PATTERN);
            Matcher m = r.matcher(text);
            Boolean isOn = false;
            if (m.find( )) {
                isOn = m.group(1).startsWith("on");

            } else {
                LOGGER.warn("error=no-pattern-match app_name=nest device_id={}", senseId);
                response.put("error", "no-pattern-match");
                response.put("result", Outcome.FAIL.getValue());
                return new HandlerResult(HandlerType.NEST, command.getValue(), response, Optional.absent());
            }
        }

        LOGGER.warn("error=no-pattern-match app_name=nest device_id={}", senseId);
        response.put("result", Outcome.FAIL.getValue());
        return new HandlerResult(HandlerType.NEST, command.getValue(), response, Optional.absent());

    }

    @Override
    public Integer matchAnnotations(final AnnotatedTranscript annotatedTranscript) {
        // TODO: add temperature
        return NO_ANNOTATION_SCORE;
    }

}
