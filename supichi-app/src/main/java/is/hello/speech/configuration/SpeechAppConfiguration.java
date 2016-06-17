package is.hello.speech.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;

public class SpeechAppConfiguration extends Configuration {

    @JsonProperty("s3_bucket")
    private S3Configuration s3Configuration;
    public S3Configuration getS3Configuration() { return s3Configuration; }

    @JsonProperty("google_api_host")
    private String googleAPIHost;
    public String getGoogleAPIHost() { return googleAPIHost; }

    @JsonProperty("google_api_port")
    private int googleAPIPort;
    public int getGoogleAPIPort() { return googleAPIPort; }

    @JsonProperty("audio_parameters")
    private AudioConfiguration audioConfiguration;
    public AudioConfiguration getAudioConfiguration() { return audioConfiguration; }
}
