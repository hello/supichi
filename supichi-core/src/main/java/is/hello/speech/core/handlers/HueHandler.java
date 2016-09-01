package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

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

    public HueHandler(final SpeechCommandDAO speechCommandDAO) {
        super("time_report", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("turn on", SpeechCommand.LIGHT_SET);
        tempMap.put("turn off", SpeechCommand.LIGHT_SET);
        tempMap.put("light on", SpeechCommand.LIGHT_SET);
        tempMap.put("light off", SpeechCommand.LIGHT_SET);
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
        return tempMap;
    }


    @Override
    public HandlerResult executeCommand(String text, String senseId, Long accountId) {
        final Optional<SpeechCommand> optionalCommand = getCommand(text); // TODO: ensure that only valid commands are returned
        final Map<String, String> response = Maps.newHashMap();

        if (optionalCommand.isPresent()) {

            final HueLight light = new HueLight();

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

        return new HandlerResult(HandlerType.HUE, response);
    }

}
