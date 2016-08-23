package is.hello.speech.utils;

import com.amazonaws.services.s3.AmazonS3;
import com.google.api.client.util.Maps;
import is.hello.speech.core.api.Response;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.UploadResponseParam;
import is.hello.speech.core.models.UploadResponseType;
import is.hello.speech.core.models.responsebuilder.BuilderResponse;
import is.hello.speech.core.models.responsebuilder.CurrentTimeResponseBuilder;
import is.hello.speech.core.models.responsebuilder.DefaultResponseBuilder;
import is.hello.speech.core.models.responsebuilder.ResponseUtils;
import is.hello.speech.core.models.responsebuilder.RoomConditionsResponseBuilder;
import is.hello.speech.core.models.responsebuilder.TriviaResponseBuilder;
import is.hello.speech.core.text2speech.AudioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Created by ksg on 7/26/16
 */
public class ResponseBuilder {
    private final static Logger LOGGER = LoggerFactory.getLogger(ResponseBuilder.class);

    private final AmazonS3 s3;
    private final String bucketName;
    private final String voiceService;
    private final String voiceName;
    private final Map<String, byte []> audioCache = Maps.newHashMap();

    public ResponseBuilder(final AmazonS3 s3, final String bucketName, final String voiceService, final String voiceName) {
        this.s3 = s3;
        this.bucketName = bucketName;
        this.voiceService = voiceService;
        this.voiceName = voiceName.equals("en-US_MichaelVoice") ? "MICHAEL" : "ALLISON";
    }

    /**
     * Creates a response
     * Format: [PB-size] + [response PB] + [audio-data]
     * @param result transcription result
     * @return bytes
     * @throws IOException
     */
    public byte[] response(final Response.SpeechResponse.Result result,
                           final boolean includeProtobuf,
                           final HandlerResult handlerResult,
                           final UploadResponseParam responseParam) throws IOException {

        LOGGER.debug("action=create-response result={} handler={}", result.toString(), handlerResult.handlerType.toString());

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // get custom response text and audio
        BuilderResponse builderResponse; // ¡uh-oh! MUTATION
        switch(handlerResult.handlerType) {
            case ROOM_CONDITIONS:
                builderResponse = RoomConditionsResponseBuilder.response(handlerResult, voiceService, voiceName);
                break;
            case TIME_REPORT:
                builderResponse = CurrentTimeResponseBuilder.response(handlerResult, voiceService, voiceName);
                break;
            case TRIVIA:
                builderResponse = TriviaResponseBuilder.response(handlerResult, voiceService, voiceName);
                break;
            default:
                builderResponse = DefaultResponseBuilder.response(result);
        }

        // get raw audio bytes from S3
        byte [] audioBytes = getAudio(builderResponse.s3Bucket, builderResponse.s3Filename);

        if (audioBytes.length == 0) {
            LOGGER.error("error=fail-to-audio-bytes action=return-UNKNOWN-error-response");
            builderResponse = DefaultResponseBuilder.response(Response.SpeechResponse.Result.UNKNOWN);
            audioBytes = getAudio(builderResponse.s3Bucket, builderResponse.s3Filename);
        }

        // return audio only
        if (!includeProtobuf) {
            LOGGER.debug("action=return-audio-only-response size={}", audioBytes.length);
            if (responseParam.type().equals(UploadResponseType.MP3)) {
                LOGGER.debug("action=convert-pcm-to-mp3 size={}", audioBytes.length);
                final AudioFormat audioFormat = AudioUtils.DEFAULT_AUDIO_FORMAT;
                final byte[] mp3Bytes = AudioUtils.encodePcmToMp3(new AudioUtils.AudioBytes(audioBytes, audioBytes.length, audioFormat));
                outputStream.write(mp3Bytes);
            } else {
                outputStream.write(audioBytes);
            }
            return outputStream.toByteArray();
        }

        // create response protobuf
        final String url = "http://s3.amazonaws.com/" + builderResponse.s3Bucket + "/" + builderResponse.s3Filename;
        final Response.SpeechResponse speechResponse = Response.SpeechResponse.newBuilder()
                .setUrl(url)
                .setResult(result)
                .setText(builderResponse.responseText)
                .setAudioStreamSize(audioBytes.length)
                .build();

        final Integer responsePBSize = speechResponse.getSerializedSize();
        LOGGER.info("action=create-response response_size={}", responsePBSize);

        final DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
        dataOutputStream.writeInt(responsePBSize);
        speechResponse.writeTo(dataOutputStream);

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

        String finalS3Bucket = bucketName;
        if (!s3Bucket.isEmpty()) {
            finalS3Bucket += "/" + s3Bucket;
        }

        final byte [] audioBytes = ResponseUtils.getAudioFromS3(s3, finalS3Bucket, filename);
        if (audioBytes.length > 0) {
            audioCache.put(filename, audioBytes);   // add to cache
        }

        return audioBytes;
    }

}
