package is.hello.speech.core.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * Created by ksg on 8/11/16
 */
public class KinesisConsumerConfiguration {

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
    @JsonProperty("streams")
    private Map< KinesisStream, String> streams;
    public ImmutableMap<KinesisStream, String> streams() {
        return ImmutableMap.copyOf(streams);
    }

    @Valid
    @NotNull
    @JsonProperty("schedule_minutes")
    private long scheduleMinutes;
    public long scheduleMinutes() { return scheduleMinutes; }

    @Valid
    @NotNull
    @JsonProperty("app_name")
    private String appName;
    public String appName() { return appName; }

    @JsonProperty("trim_horizon")
    private Boolean trimHorizon = Boolean.TRUE;
    public Boolean trimHorizon() {return trimHorizon;}

    @Valid
    @NotNull
    @JsonProperty("max_records")
    private int maxRecord;
    public int maxRecord() { return maxRecord; }

}
