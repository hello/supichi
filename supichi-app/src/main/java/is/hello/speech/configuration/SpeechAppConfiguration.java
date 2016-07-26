package is.hello.speech.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hello.suripu.coredw8.configuration.MessejiHttpClientConfiguration;
import com.hello.suripu.coredw8.configuration.NewDynamoDBConfiguration;
import io.dropwizard.Configuration;
import io.dropwizard.db.DataSourceFactory;
import is.hello.speech.core.configuration.SQSConfiguration;
import is.hello.speech.core.configuration.SaveAudioConfiguration;
import is.hello.speech.core.configuration.WatsonConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

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

    @NotNull
    @JsonProperty("messeji_http_client")
    private MessejiHttpClientConfiguration messejiHttpClientConfiguration;
    public MessejiHttpClientConfiguration getMessejiHttpClientConfiguration() { return messejiHttpClientConfiguration; }

    @Valid
    @NotNull
    @JsonProperty("dynamodb")
    private NewDynamoDBConfiguration dynamoDBConfiguration;
    public NewDynamoDBConfiguration dynamoDBConfiguration(){
        return dynamoDBConfiguration;
    }

    @Valid
    @NotNull
    @JsonProperty("common_db")
    private DataSourceFactory commonDB = new DataSourceFactory();
    public DataSourceFactory getCommonDB() {
        return commonDB;
    }

    @Valid
    @NotNull
    @JsonProperty("sqs_configuration")
    private SQSConfiguration sqsConfiguration = new SQSConfiguration();
    public SQSConfiguration getSqsConfiguration() { return sqsConfiguration; }

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

}