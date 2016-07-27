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
    private String bucketName;
    public String getBucketName() { return bucketName; }

    @Valid
    @NotNull
    @JsonProperty("s3_audio_prefix_raw")
    private  String audioPrefixRaw;
    public String getAudioPrefixRaw() { return audioPrefixRaw; }

    @Valid
    @NotNull
    @JsonProperty("s3_audio_prefix")
    private String audioPrefix;
    public String getAudioPrefix() { return audioPrefix; }

}
