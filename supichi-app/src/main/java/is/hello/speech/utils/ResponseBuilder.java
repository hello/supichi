package is.hello.speech.utils;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.IOUtils;
import com.google.api.client.util.Maps;
import com.google.common.base.Optional;
import com.hello.suripu.core.models.Sensor;
import is.hello.speech.core.api.Response;
import is.hello.speech.core.db.DefaultResponseDAO;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
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
        if (handlerResult.handlerType.equals(HandlerType.ROOM_CONDITIONS)) {
            Optional<UploadResponse> optionalUploadResponse = getRoomConditionsResponse(result, handlerResult);
            if (optionalUploadResponse.isPresent()) {
                uploadResponse = optionalUploadResponse.get();
            } else {
                LOGGER.error("action=fail-to-get-custom-response remedy=return-UNKNOWN-error-response");
                uploadResponse = defaultResponseDAO.getResponse(Response.SpeechResponse.Result.UNKNOWN);
            }
        } else {
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

    private Optional<UploadResponse> getRoomConditionsResponse(final Response.SpeechResponse.Result result, HandlerResult handlerResult) {
        String s3Bucket = "ROOM_CONDITIONS";
        String filename = "ROOM_CONDITIONS-GET_SENSOR";
        String sensorName = "";
        String responseText;
        byte [] audioBytes;

        if (handlerResult.responseParameters.containsKey("sensor")) {
            sensorName = handlerResult.responseParameters.get("sensor").toUpperCase();
            filename += "-" + sensorName;
            s3Bucket += String.format("/%s", sensorName);
        }

        final String outcomeString = handlerResult.responseParameters.get("result");
        final HandlerResult.Outcome outcome = HandlerResult.Outcome.fromString(outcomeString);

        // compose filename from results
        if (outcome.equals(HandlerResult.Outcome.FAIL)) {
            // error message
            s3Bucket = "";
            filename = "default_rejected-WATSON-MICHAEL-compressed.ima";
            responseText = "Sorry, your command is rejected.";

        } else {

            // valid response
            if (!sensorName.isEmpty()) {

                final String sensorValue = handlerResult.responseParameters.get("value");
                String params = String.format("value_%s", sensorValue);

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

                filename += String.format("-%s-%s-%s-compressed.ima", params, voiceService, voiceName);
                responseText = String.format("The %s in your room is %s %s.", sensorName, sensorValue, unit);
            } else {
                s3Bucket = "";
                filename = "default_try_again-WATSON-MICHAEL-compressed.ima";
                responseText = "Sorry, your command cannot be processed. Please try again.";
            }
        }

        LOGGER.debug("action=get-filename name={}", filename);

        if (audioCache.containsKey(filename)) {
            LOGGER.debug("action=found-audio-in-cache");
            audioBytes = audioCache.get(filename);

        } else {

            try {
                // fetch file from S3
                s3Bucket = bucketName + "/" + s3Bucket;
                LOGGER.debug("action=fetching-audio-from-s3 bucket={} key={}", s3Bucket, filename);

                final S3Object object = s3.getObject(s3Bucket, filename);
                final InputStream inputStream = object.getObjectContent();
                byte [] bytes = IOUtils.toByteArray(inputStream);
                audioBytes = Arrays.copyOfRange(bytes, HEADER_SIZE, bytes.length); // headerless
                audioCache.put(filename, audioBytes);
            } catch (IOException e) {
                LOGGER.error("error=fail-to-get-audio bucket={} key={} error_msg={}", s3Bucket, filename, e.getMessage());
                return Optional.absent();
            } catch (AmazonS3Exception e) {
                LOGGER.error("error=fail-to-get-audio bucket={} key={} error_msg={}", s3Bucket, filename, e.getMessage());
                return Optional.absent();
            }
        }

        final Response.SpeechResponse response = Response.SpeechResponse.newBuilder()
                .setUrl("http://s3.amazonaws.com/" + s3Bucket + "/" + filename)
                .setResult(result)
                .setText(responseText)
                .setAudioStreamSize(audioBytes.length)
                .build();

        return Optional.of(new UploadResponse(response, audioBytes));

    }
}
