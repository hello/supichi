package is.hello.speech.utils.responsebuilder;

import is.hello.speech.core.models.BuilderResponse;
import is.hello.speech.core.models.HandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by ksg on 8/8/16
 */
public class TriviaResponseBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(TriviaResponseBuilder.class);

    // MUST-HAVES
    private static final String BUCKET_NAME = "TRIVIA/TRIVIA_INFO";
    private static final String FILENAME_PREFIX = "TRIVIA-GET_TRIVIA-TRIVIA_INFO";

    // Builder-related
    private static final String TRIVIA_ERROR_TEXT = "Sorry, I'm not able to determine the time right now. Please try again later.";
    private static final String TRIVIA_ERROR_FILENAME_TEMPLATE = "-no_data-%s-%s-16k.wav";

    private static final String RESPONSE_TEXT_FORMATTER = "The time is %s.";


    public static BuilderResponse response(final HandlerResult handlerResult, final String voiceService, final String voiceName) {
        String s3Bucket = BUCKET_NAME;
        String filename = FILENAME_PREFIX;
        final String responseText;

        final HandlerResult.Outcome outcome = ResponseUtils.getOutcome(handlerResult);
        if (outcome.equals(HandlerResult.Outcome.OK)) {
            final String answer = handlerResult.responseParameters.get("answer");
            filename += String.format("-%s-%s-%s-16k.wav", answer, voiceService, voiceName);
            responseText = handlerResult.responseParameters.get("text");

        } else {
            s3Bucket = "";
            filename = String.format(ResponseUtils.REJECT_ERROR_FILENAME_TEMPLATE, voiceName);
            responseText = ResponseUtils.REJECT_ERROR_TEXT;
        }

        return new BuilderResponse(s3Bucket, filename, responseText);

//        final byte[] audioBytes = getAudio(s3Bucket, filename);
//
//        if (audioBytes == null) {
//            LOGGER.error("error=fail-to-audio-bytes action=return-UNKNOWN-error-response");
//            return defaultResponseDAO.getResponse(Response.SpeechResponse.Result.UNKNOWN);
//        }
//
//        final Response.SpeechResponse response = Response.SpeechResponse.newBuilder()
//                .setUrl("http://s3.amazonaws.com/" + s3Bucket + "/" + filename)
//                .setResult(result)
//                .setText(responseText)
//                .setAudioStreamSize(audioBytes.length)
//                .build();
//
//        return new UploadResponse(response, audioBytes);
    }

}
