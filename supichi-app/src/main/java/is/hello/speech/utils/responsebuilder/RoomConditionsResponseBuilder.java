package is.hello.speech.utils.responsebuilder;

import com.google.api.client.util.Maps;
import com.hello.suripu.core.models.Sensor;
import is.hello.speech.core.models.BuilderResponse;
import is.hello.speech.core.models.HandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;

/**
 * Created by ksg on 7/26/16
 */
public class RoomConditionsResponseBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomConditionsResponseBuilder.class);

    // MUST-HAVES
    private static final String BUCKET_NAME = "ROOM_CONDITIONS";
    private static final String FILENAME_PREFIX = "ROOM_CONDITIONS-GET_SENSOR";

    // Builder-related
    private static final String ERROR_NO_DATA = "no data";
    private static final String ERROR_DATA_TOO_OLD = "data too old";

    private static final String CUSTOM_ERROR_FORMATTER = "Sorry, I wasn't able to access your %s data right now. Please try again later";
    private static final String CUSTOM_ERROR_FILENAME_TEMPLATE = "-no_data-WATSON-%s-16k.wav";

    private static final String RESPONSE_TEXT_FORMATTER = "The %s in your room is %s %s.";

    private final static Map<String, Integer> numOptions;
    static {
        numOptions = Maps.newHashMap();
        numOptions.put(Sensor.TEMPERATURE.toString(), 4);
        numOptions.put(Sensor.HUMIDITY.toString(), 2);
        numOptions.put(Sensor.LIGHT.toString(), 2);
        numOptions.put(Sensor.SOUND.toString(), 2);
        numOptions.put(Sensor.PARTICULATES.toString(), 2);
    }

    public static BuilderResponse response(final HandlerResult handlerResult,
                                                      final String voiceService,
                                                      final String voiceName) {
        String s3Bucket = BUCKET_NAME;
        String filename = FILENAME_PREFIX;
        String sensorName = "";
        String responseText;

        if (handlerResult.responseParameters.containsKey("sensor")) {
            sensorName = handlerResult.responseParameters.get("sensor").toUpperCase();
            filename += "-" + sensorName;
            s3Bucket += String.format("/%s", sensorName);
        }

        final HandlerResult.Outcome outcome = ResponseUtils.getOutcome(handlerResult);

        // compose filename from outcomes
        if (outcome.equals(HandlerResult.Outcome.FAIL)) {

            final String errorMessage = handlerResult.responseParameters.get("error");
            if (errorMessage.equalsIgnoreCase(ERROR_NO_DATA) || errorMessage.equalsIgnoreCase(ERROR_DATA_TOO_OLD)) {
                // custom errors
                filename += String.format(CUSTOM_ERROR_FILENAME_TEMPLATE, voiceName);
                responseText = String.format(CUSTOM_ERROR_FORMATTER, sensorName);
            } else {
                // return generic error
                s3Bucket = "";
                filename = String.format(ResponseUtils.REJECT_ERROR_FILENAME_TEMPLATE, voiceName);
                responseText = ResponseUtils.REJECT_ERROR_TEXT;
            }

        } else {

            // process valid response
            if (!sensorName.isEmpty()) {

                // value
                final String sensorValue = handlerResult.responseParameters.get("value");
                String params = String.format("value_%s", sensorValue);

                // units
                final String unit = handlerResult.responseParameters.get("unit");
                if (sensorName.equalsIgnoreCase(Sensor.TEMPERATURE.toString()) && unit.equals("ºF")) {
                    params += "_F";
                }

                // randomly pick one of the responses
                final int maxOptions = numOptions.get(sensorName.toLowerCase());
                if (maxOptions > 1) {
                    final Random random = new Random();
                    final int option = random.nextInt(maxOptions) + 1;
                    params += String.format("-opt_%d", option);
                }

                // special cases
                if (sensorName.equalsIgnoreCase(Sensor.LIGHT.toString()) && sensorValue.equals("0")) {
                    params = "under_1_lux-opt_1";
                }

                filename += String.format("-%s-%s-%s-16k.wav", params, voiceService, voiceName);
                responseText = String.format(RESPONSE_TEXT_FORMATTER, sensorName, sensorValue, unit);

            } else {
                s3Bucket = "";
                filename = String.format("default_try_again-WATSON-%s-16k.wav", voiceName);
                responseText = "Sorry, your command cannot be processed. Please try again.";
            }
        }

        LOGGER.debug("action=get-filename name={}", filename);

        return new BuilderResponse(s3Bucket, filename, responseText);
    }

}
