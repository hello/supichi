package is.hello.speech.utils;

import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.AudioFormat;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.Voice;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.text2speech.AudioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by ksg on 8/3/16
 */
public class WatsonResponseBuilder {
    private final static Logger LOGGER = LoggerFactory.getLogger(WatsonResponseBuilder.class);

    private final TextToSpeech watson;
    private final Voice watsonVoice;

    public WatsonResponseBuilder(final TextToSpeech watson, final String voice) {
        this.watson = watson;
        this.watsonVoice = this.watson.getVoice(voice).execute();

    }

    public byte[] response(HandlerResult executeResult) {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        final String text = executeResult.responseParameters.get("text");
        try (final InputStream watsonStream = watson.synthesize(text, watsonVoice, AudioFormat.WAV).execute()) {
            final AudioUtils.AudioBytes watsonAudio = AudioUtils.convertStreamToBytesWithWavHeader(watsonStream);

            // down-sample audio from 22050 to 16k, upload converted bytes to S3
            final AudioUtils.AudioBytes downSampledBytes = AudioUtils.downSampleAudio(watsonAudio.bytes, 16000.0f);

            outputStream.write(downSampledBytes.bytes);
        } catch (IOException e) {
            LOGGER.error("action=watson-down-sample-fails error_msg={}", e.getMessage());
        }


        return outputStream.toByteArray();
    }
}
