package is.hello.speech.workers.framework;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Created by ksg on 6/28/16
 */
public class WorkerConfiguration extends Configuration {
    @Valid
    @NotNull
    @JsonProperty("watson")
    private WatsonConfiguration watsonConfiguration;
    public WatsonConfiguration getWatsonConfiguration() { return watsonConfiguration;}

    @Valid
    @NotNull
    @JsonProperty("save_audio")
    private SaveAudioConfiguration saveAudioConfiguration;
    public SaveAudioConfiguration getSaveAudioConfiguration() { return saveAudioConfiguration;}

    @Valid
    @NotNull
    @JsonProperty("SQS_configuration")
    private SQSConfiguration sqsConfiguration = new SQSConfiguration();
    public SQSConfiguration getSqsConfiguration() { return sqsConfiguration; }

}
