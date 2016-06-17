package is.hello.speech.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.speech.v1.InitialRecognizeRequest.AudioEncoding;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Created by ksg on 6/16/16
 */
public class AudioConfiguration {
    @Valid
    @NotNull
    @JsonProperty("interim_results")
    private Boolean interimResults;
    public Boolean getInterimResults() { return interimResults; }

    @Valid
    @NotNull
    @JsonProperty("encoding")
    private AudioEncoding encoding;
    public AudioEncoding getEncoding() { return encoding; }

    @Valid
    @NotNull
    @JsonProperty("buffer_size")
    private int bufferSize;
    public int getBufferSize() { return bufferSize; }

}
