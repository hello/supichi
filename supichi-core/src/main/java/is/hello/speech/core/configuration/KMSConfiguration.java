package is.hello.speech.core.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Created by ksg on 8/19/16
 */
public class KMSConfiguration {
    public class Keys {
        @Valid
        @NotNull
        @JsonProperty("uuid")
        private String uuidKey;
        public String uuid() { return this.uuidKey; }

        @Valid
        @NotNull
        @JsonProperty("audio")
        private String audioKey;
        public String audio() { return this.audioKey; }
    }

    @Valid
    @NotNull
    @JsonProperty("endpoint")
    private String endpoint;
    public String endpoint() { return this.endpoint; }


    @Valid
    @NotNull
    @JsonProperty("keys")
    private Keys kmsKeys;
    public Keys kmsKeys() { return this.kmsKeys; }
}
