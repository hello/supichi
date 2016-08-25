package is.hello.speech.kinesis;

import com.hello.suripu.core.speech.SpeechResult;

/**
 * Created by ksg on 8/11/16
 */
public class KinesisData {
    public final SpeechResult speechResult;
    public final byte [] audioData;


    public KinesisData(final SpeechResult speechResult, final byte[] audioData) {
        this.speechResult = speechResult;
        this.audioData = audioData;
    }
}
