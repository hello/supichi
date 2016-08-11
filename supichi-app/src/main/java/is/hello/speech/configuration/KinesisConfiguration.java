package is.hello.speech.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * Created by ksg on 8/9/16
 */
public class KinesisConfiguration {

    public enum Stream {
        AUDIO("audio"),
        SPEECH_RESULT("speech_result");
        private String value;

        Stream(String text) {
            value = text;
        }

        public String value() {
            return value;
        }

        @JsonCreator
        public Stream fromString(final String val) {
            return Stream.getFromString(val);
        }

        public static Stream getFromString(final String val) {
            final Stream[] names = Stream.values();

            for (final Stream name: names) {
                if (name.value.equalsIgnoreCase(val)) {
                    return name;
                }
            }

            throw new IllegalArgumentException(String.format("%s is not a valid Speech KinesisStreamName", val));
        }
    }

    @Valid
    @NotNull
    @JsonProperty("endpoint")
    private String endpoint;
    public String endpoint() { return this.endpoint; }

    @Valid
    @NotNull
    @JsonProperty("region")
    private String region;
    public String region() { return this.region; }

    @Valid
    @NotNull
    @JsonProperty("max_connections")
    private long maxConnections;
    public long maxConnections() { return this.maxConnections; }


    @Valid
    @NotNull
    @JsonProperty("request_timeout")
    private long requstTimeout;
    public long requstTimeout() { return this.requstTimeout; }

    @Valid
    @NotNull
    @JsonProperty("record_max_buffered_time")
    private long recordMaxBufferedTime;
    public long recordMaxBufferedTime() { return this.recordMaxBufferedTime; }

    @Valid
    @NotNull
    @JsonProperty("queue_size")
    private int queueSize;
    public int queueSize() { return this.queueSize; }

    @Valid
    @NotNull
    @JsonProperty("streams")
    private Map<Stream, String> streams;
    public ImmutableMap<Stream, String> streams() {
        return ImmutableMap.copyOf(streams);
    }

}