package is.hello.speech.utils;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.IOUtils;
import com.google.api.client.util.Maps;
import com.hello.suripu.core.models.Sensor;
import is.hello.speech.core.api.Response;
import is.hello.speech.core.db.DefaultResponseDAO;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.UploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

/**
 * Created by ksg on 7/26/16
 */
public class ResponseBuilder {
    private final static Logger LOGGER = LoggerFactory.getLogger(ResponseBuilder.class);

    private final static String ERROR_NO_SENSE_PAIR = "no paired sense";
    private final static String ERROR_NO_DATA = "no data";
    private final static String ERROR_DATA_TOO_OLD = "data too old";

    private final static int HEADER_SIZE = 44;

    private final AmazonS3 s3;
    private final String bucketName;
    private final DefaultResponseDAO defaultResponseDAO;
    private final String voiceService;
    private final String voiceName;
    private final Map<String, Integer> numOptions = Maps.newHashMap();
    private final Map<String, byte []> audioCache = Maps.newHashMap();

    public ResponseBuilder(final AmazonS3 s3, final String bucketName, final DefaultResponseDAO defaultResponseDAO,
                           final String voiceService, final String voiceName) {
        this.s3 = s3;
        this.bucketName = bucketName;
        this.defaultResponseDAO = defaultResponseDAO;
        this.voiceService = voiceService;
        if (voiceName.equals("en-US_MichaelVoice")) {
            this.voiceName = "MICHAEL";
        } else {
            this.voiceName = "ALLISON";
        }

        // TODO: Grab these from a centralized source
        numOptions.put(Sensor.TEMPERATURE.toString(), 4);
        numOptions.put(Sensor.HUMIDITY.toString(), 2);
        numOptions.put(Sensor.LIGHT.toString(), 2);
        numOptions.put(Sensor.SOUND.toString(), 2);
        numOptions.put(Sensor.PARTICULATES.toString(), 2);
    }

    /**
     * Creates a response
     * Format: [PB-size] + [response PB] + [audio-data]
     * @param result transcription result
     * @return bytes
     * @throws IOException
     */
    public byte[] response(final Response.SpeechResponse.Result result, final boolean includeProtobuf, final HandlerResult handlerResult) throws IOException {

        LOGGER.debug("action=create-response result={} handler={}", result.toString(), handlerResult.handlerType.toString());

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // return custom response if available

        final UploadResponse uploadResponse;
        switch(handlerResult.handlerType) {
            case ROOM_CONDITIONS:
                uploadResponse = getRoomConditionsResponse(result, handlerResult);
                break;
            case TIME_REPORT:
                uploadResponse = getTimeResponse(result, handlerResult);
                break;
            case TRIVIA:
                uploadResponse = getTriviaResponse(result, handlerResult);
                break;
            default:
                uploadResponse = defaultResponseDAO.getResponse(result);
        }

        final Response.SpeechResponse response = uploadResponse.response;

        final Integer responsePBSize = response.getSerializedSize();
        LOGGER.info("action=create-response response_size={}", responsePBSize);

        if (!includeProtobuf) {
            LOGGER.debug("action=return-audio-only-response");
            outputStream.write(uploadResponse.audio_bytes);

        } else {

            final DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

            dataOutputStream.writeInt(responsePBSize);
            response.writeTo(dataOutputStream);

            final byte[] audioData = uploadResponse.audio_bytes;
            LOGGER.info("action=create-response audio_size={}", audioData.length);

            dataOutputStream.write(audioData);

            LOGGER.info("action=get-output-stream-size size={}", outputStream.size());
        }

        return outputStream.toByteArray();
    }

    private UploadResponse getTriviaResponse(final Response.SpeechResponse.Result result, final HandlerResult handlerResult) {
        String s3Bucket = "TRIVIA/TRIVIA_INFO";
        String filename = "TRIVIA-GET_TRIVIA-TRIVIA_INFO";
        final String responseText;

        final HandlerResult.Outcome outcome = ResponseUtils.getOutcome(handlerResult);
        if (outcome.equals(HandlerResult.Outcome.OK)) {
            final String answer = handlerResult.responseParameters.get("answer");
            filename += String.format("-%s-WATSON-%s-16k.wav", answer, voiceName);
            responseText = handlerResult.responseParameters.get("text");

        } else {
            s3Bucket = "";
            filename = String.format("default_rejected-WATSON-%s-16k.wav", voiceName);
            responseText = "Sorry, I wasn't able to understand.";
        }

        final byte[] audioBytes = getAudio(s3Bucket, filename);

        if (audioBytes == null) {
            LOGGER.error("error=fail-to-audio-bytes action=return-UNKNOWN-error-response");
            return defaultResponseDAO.getResponse(Response.SpeechResponse.Result.UNKNOWN);
        }

        final Response.SpeechResponse response = Response.SpeechResponse.newBuilder()
                .setUrl("http://s3.amazonaws.com/" + s3Bucket + "/" + filename)
                .setResult(result)
                .setText(responseText)
                .setAudioStreamSize(audioBytes.length)
                .build();

        return new UploadResponse(response, audioBytes);
    }


    private UploadResponse getTimeResponse(final Response.SpeechResponse.Result result, final HandlerResult handlerResult) {

        final String s3Bucket = "TIME_REPORT/TIME";
        String filename = "TIME_REPORT-GET_TIME-TIME";
        final String responseText;

        final HandlerResult.Outcome outcome = ResponseUtils.getOutcome(handlerResult);
        if (outcome.equals(HandlerResult.Outcome.OK)) {
            final String timeString = handlerResult.responseParameters.get("time");
            filename += String.format("-%s-WATSON-%s-16k.wav", timeString, voiceName);
            responseText = String.format("The time is %s", timeString);
        } else {
            responseText = "Sorry, I'm not able to determine the time right now. Please try again later.";
            filename += String.format("-no_data-WATSON-%s-16k.wav", voiceName);
        }

        final byte [] audioBytes = getAudio(s3Bucket, filename);

        if (audioBytes == null) {
            LOGGER.error("error=fail-to-audio-bytes action=return-UNKNOWN-error-response");
            return defaultResponseDAO.getResponse(Response.SpeechResponse.Result.UNKNOWN);
        }

        final Response.SpeechResponse response = Response.SpeechResponse.newBuilder()
                .setUrl("http://s3.amazonaws.com/" + s3Bucket + "/" + filename)
                .setResult(result)
                .setText(responseText)
                .setAudioStreamSize(audioBytes.length)
                .build();

        return new UploadResponse(response, audioBytes);
    }

    // TODO: refactor this
    private UploadResponse getRoomConditionsResponse(final Response.SpeechResponse.Result result, HandlerResult handlerResult) {
        String s3Bucket = "ROOM_CONDITIONS";
        String filename = "ROOM_CONDITIONS-GET_SENSOR";
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
            if (errorMessage.equalsIgnoreCase("no data") || errorMessage.equalsIgnoreCase("data too old")) {
                // custom errors
                filename += String.format("-no_data-WATSON-%s-16k.wav", voiceName);
                responseText = String.format("Sorry, I wasn't able to access your %s data right now. Please try again later", sensorName);
            } else {
                // return generic error
                s3Bucket = "";
                filename = String.format("default_rejected-WATSON-%s-16k.wav", voiceName);
                responseText = "Sorry, I wasn't able to understand.";
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
                responseText = String.format("The %s in your room is %s %s.", sensorName, sensorValue, unit);

            } else {
                s3Bucket = "";
                filename = String.format("default_try_again-WATSON-%s-16k.wav", voiceName);
                responseText = "Sorry, your command cannot be processed. Please try again.";
            }
        }

        LOGGER.debug("action=get-filename name={}", filename);

        final byte [] audioBytes = getAudio(s3Bucket, filename);

        if (audioBytes == null) {
            LOGGER.error("error=fail-to-audio-bytes action=return-UNKNOWN-error-response");
            return defaultResponseDAO.getResponse(Response.SpeechResponse.Result.UNKNOWN);
        }

        final Response.SpeechResponse response = Response.SpeechResponse.newBuilder()
                .setUrl("http://s3.amazonaws.com/" + s3Bucket + "/" + filename)
                .setResult(result)
                .setText(responseText)
                .setAudioStreamSize(audioBytes.length)
                .build();

        return new UploadResponse(response, audioBytes);

    }

    private byte [] getAudio(final String s3Bucket, final String filename) {
        byte [] audioBytes = null;
        if (audioCache.containsKey(filename)) {
            LOGGER.debug("action=found-audio-in-cache");
            return audioCache.get(filename);
        }

        // fetch file from S3
        try {
            final String finalS3Bucket = bucketName + "/" + s3Bucket;
            LOGGER.debug("action=fetching-audio-from-s3 bucket={} key={}", finalS3Bucket, filename);

            final S3Object object = s3.getObject(finalS3Bucket, filename);

            final InputStream inputStream = object.getObjectContent();
            byte [] bytes = IOUtils.toByteArray(inputStream);
            audioBytes = Arrays.copyOfRange(bytes, HEADER_SIZE, bytes.length); // headerless

            audioCache.put(filename, audioBytes);   // add to cache

        } catch (IOException | AmazonS3Exception e) {
            LOGGER.error("error=fail-to-get-audio bucket={} key={} error_msg={}", s3Bucket, filename, e.getMessage());
        }

        return audioBytes;
    }

}
