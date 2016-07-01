package is.hello.speech.core.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Created by ksg on 6/28/16
 */
public class SaveAudioConfiguration {
    @Valid
    @NotNull
    @JsonProperty("s3_bucket_name")
    private final String bucketName = "hello-audio";
    public String getBucketName() { return bucketName; }

    @Valid
    @NotNull
    @JsonProperty("s3_audio_prefix")
    private final String audioPrefix = "voice/watson-text2speech/raw";
    public String getAudioPrefix() { return audioPrefix; }

}
