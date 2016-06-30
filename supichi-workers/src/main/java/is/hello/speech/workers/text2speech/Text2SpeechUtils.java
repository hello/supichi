package is.hello.speech.workers.text2speech;


import org.apache.commons.codec.binary.Base64;

import java.io.IOException;

/**
 * Created by ksg on 6/29/16
 */
public class Text2SpeechUtils {

    public static String encodeMessage(final Text2SpeechQueue.SynthesizeMessage synthesizeMessage) {
        final byte[] bytes = synthesizeMessage.toByteArray();
        return Base64.encodeBase64URLSafeString(bytes);
    }

    public static Text2SpeechQueue.SynthesizeMessage decodeMessage(final String sqsMessage) throws IOException {
        final byte[] bytes = Base64.decodeBase64(sqsMessage);
        return Text2SpeechQueue.SynthesizeMessage.parseFrom(bytes);
    }
}
