package is.hello.speech.core.models;

import is.hello.speech.core.api.Response;

/**
 * Created by ksg on 7/26/16
 */
public class UploadResponse {
    public Response.SpeechResponse response;
    public final byte [] audio_bytes;

    public UploadResponse(Response.SpeechResponse response, byte[] audio_bytes) {
        this.response = response;
        this.audio_bytes = audio_bytes; // without header
    }

    public Response.SpeechResponse getResponse() {
        return this.response;
    }
}
