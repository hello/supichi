package is.hello.speech.resources.v1;

import is.hello.speech.core.api.Speech;

/**
 * Created by ksg on 9/8/16
 */
public class UploadData {
    public final int protobufSize;
    public final Speech.speech_data speechData;
    public final byte[] audioBody;

    public UploadData(final int protobufSize, final Speech.speech_data speechData, final byte[] audioBody) {
        this.protobufSize = protobufSize;
        this.speechData = speechData;
        this.audioBody = audioBody;
    }
}
