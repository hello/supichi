package is.hello.speech.workers.text2speech;

import com.google.common.base.Optional;

/**
 * Created by ksg on 6/29/16
 */
public class Text2SpeechMessage {
    public final String messageHandler;
    public final String messageId;
    public final Optional<Text2SpeechQueue.SynthesizeMessage> synthesizeMessage;


    public Text2SpeechMessage(final String messageHandler, final String messageId,
                              final Optional<Text2SpeechQueue.SynthesizeMessage> synthesizeMessage) {
        this.messageHandler = messageHandler;
        this.messageId = messageId;
        this.synthesizeMessage = synthesizeMessage;
    }
}
