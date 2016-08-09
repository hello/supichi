package is.hello.speech.utils.responsebuilder;

import com.amazonaws.services.s3.AmazonS3;
import com.google.api.client.util.Maps;
import com.google.common.base.Optional;
import is.hello.speech.core.api.Response;
import is.hello.speech.core.db.DefaultResponseDAO;
import is.hello.speech.core.models.BuilderResponse;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.UploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Created by ksg on 7/26/16
 */
public class ResponseBuilder {
    private final static Logger LOGGER = LoggerFactory.getLogger(ResponseBuilder.class);

    private final static String ERROR_NO_SENSE_PAIR = "no paired sense";
    private final static String ERROR_NO_DATA = "no data";
    private final static String ERROR_DATA_TOO_OLD = "data too old";


    private final AmazonS3 s3;
    private final String bucketName;
    private final DefaultResponseDAO defaultResponseDAO;
    private final String voiceService;
    private final String voiceName;
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
        final UploadResponse defaultResponse = defaultResponseDAO.getResponse(result);

        Optional<BuilderResponse> optionalBuilderResponse = Optional.absent();
        byte[] audioBytes = new byte[0];
        Optional<Response.SpeechResponse> optionalSpeechResponse = Optional.absent();

        switch(handlerResult.handlerType) {
            case ROOM_CONDITIONS:
                optionalBuilderResponse = Optional.of(RoomConditionsResponseBuilder.response(handlerResult, voiceService, voiceName));
                break;
            case TIME_REPORT:
                optionalBuilderResponse = Optional.of(CurrentTimeResponseBuilder.response(handlerResult, voiceService, voiceName));
                break;
            case TRIVIA:
                optionalBuilderResponse = Optional.of(TriviaResponseBuilder.response(handlerResult, voiceService, voiceName));
                break;
            default:
                audioBytes = defaultResponse.audio_bytes;
                optionalSpeechResponse = Optional.of(defaultResponse.response);
        }

        // custom response available
        if (optionalBuilderResponse.isPresent()) {
            // get audio from S3
            final BuilderResponse builderResponse = optionalBuilderResponse.get();
            audioBytes = getAudio(builderResponse.s3Bucket, builderResponse.s3Filename);

            if (audioBytes.length == 0) {
                LOGGER.error("error=fail-to-audio-bytes action=return-UNKNOWN-error-response");
                final UploadResponse unknownError = defaultResponseDAO.getResponse(Response.SpeechResponse.Result.UNKNOWN);
                audioBytes = unknownError.audio_bytes;
                optionalSpeechResponse = Optional.of(unknownError.response);
            }
        }

        // return audio only
        if (!includeProtobuf) {
            LOGGER.debug("action=return-audio-only-response size={}", audioBytes.length);
            outputStream.write(audioBytes);
            return outputStream.toByteArray();
        }

        // return with protobuf
        final Response.SpeechResponse response;
        if (optionalSpeechResponse.isPresent()) {
            response = optionalSpeechResponse.get();
        } else {
            if (optionalBuilderResponse.isPresent()) {
                // create protobuf
                final BuilderResponse builderResponse = optionalBuilderResponse.get();
                final String url = "http://s3.amazonaws.com/" + builderResponse.s3Bucket + "/" + builderResponse.s3Filename;
                response = Response.SpeechResponse.newBuilder()
                        .setUrl(url)
                        .setResult(result)
                        .setText(builderResponse.responseText)
                        .setAudioStreamSize(audioBytes.length)
                        .build();
            } else {
                response = defaultResponse.response;
            }
        }

        final Integer responsePBSize = response.getSerializedSize();
        LOGGER.info("action=create-response response_size={}", responsePBSize);

        final DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

        dataOutputStream.writeInt(responsePBSize);
        response.writeTo(dataOutputStream);

        LOGGER.info("action=create-response audio_size={}", audioBytes.length);
        dataOutputStream.write(audioBytes);

        LOGGER.info("action=get-output-stream-size size={}", outputStream.size());
        return outputStream.toByteArray();
    }


    private byte [] getAudio(final String s3Bucket, final String filename) {

        if (audioCache.containsKey(filename)) {
            LOGGER.debug("action=found-audio-in-cache");
            return audioCache.get(filename);
        }

        final String finalS3Bucket = bucketName + "/" + s3Bucket;
        final byte [] audioBytes = ResponseUtils.getAudioFromS3(s3, finalS3Bucket, filename);
        if (audioBytes.length > 0) {
            audioCache.put(filename, audioBytes);   // add to cache
        }

        return audioBytes;
    }

}
