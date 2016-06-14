package is.hello.speech.server;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;

public class SpeechConfiguration extends Configuration {

    @JsonProperty("s3_bucket")
    private String s3Bucket = "hello-firmware-public";
    public String s3Bucket() {
        return s3Bucket;
    }
}
