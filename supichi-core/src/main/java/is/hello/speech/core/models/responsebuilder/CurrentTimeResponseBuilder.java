package is.hello.speech.core.models.responsebuilder;

import is.hello.speech.core.handlers.results.Outcome;
import is.hello.speech.core.models.HandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by ksg on 8/8/16
 */
public class CurrentTimeResponseBuilder implements ResponseBuilderInterface{

    private static final Logger LOGGER = LoggerFactory.getLogger(CurrentTimeResponseBuilder.class);

    // MUST-HAVES
    private static final String BUCKET_NAME = "TIME_REPORT/TIME";
    private static final String FILENAME_PREFIX = "TIME_REPORT-GET_TIME-TIME";

    // Builder-related
    private static final String TIME_ERROR_TEXT = "Sorry, I'm not able to determine the time right now. Please try again later.";
    private static final String TIME_ERROR_FILENAME_TEMPLATE = "-no_data-%s-%s-16k.wav";

    private static final String RESPONSE_TEXT_FORMATTER = "The time is %s.";


    public static BuilderResponse response(final HandlerResult handlerResult, final String voiceService, final String voiceName) {

        String filename = FILENAME_PREFIX;
        final String responseText;

        final Outcome outcome = ResponseUtils.getOutcome(handlerResult);
        if (outcome.equals(Outcome.OK)) {
            final String timeString = handlerResult.responseParameters.get("time");
            filename += String.format("-%s-%s-%s-16k.wav", timeString, voiceService, voiceName);
            responseText = String.format(RESPONSE_TEXT_FORMATTER, timeString);
        } else {
            responseText = TIME_ERROR_TEXT;
            filename += String.format(TIME_ERROR_FILENAME_TEMPLATE, voiceService, voiceName);
        }

        return new BuilderResponse(BUCKET_NAME, filename, responseText);
    }
}
