package is.hello.speech.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hello.suripu.coredropwizard.configuration.MessejiHttpClientConfiguration;
import com.hello.suripu.coredropwizard.configuration.NewDynamoDBConfiguration;
import io.dropwizard.Configuration;
import io.dropwizard.db.DataSourceFactory;
import is.hello.speech.core.configuration.KMSConfiguration;
import is.hello.speech.core.configuration.KinesisConsumerConfiguration;
import is.hello.speech.core.configuration.KinesisProducerConfiguration;
import is.hello.speech.core.configuration.S3AudioConfiguration;
import is.hello.speech.core.configuration.SQSConfiguration;
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
    @JsonProperty("watson_save_audio")
    private S3AudioConfiguration watsonAudioConfiguration;
    public S3AudioConfiguration watsonAudioConfiguration() { return watsonAudioConfiguration;}

    @Valid
    @NotNull
    @JsonProperty("sense_upload_audio")
    private S3AudioConfiguration senseUploadAudioConfiguration;
    public S3AudioConfiguration senseUploadAudioConfiguration() { return senseUploadAudioConfiguration;}

    @JsonProperty("consumer_enabled")
    private Boolean consumerEnabled = false;
    public Boolean consumerEnabled() {
        return consumerEnabled;
    }

    @JsonProperty("forecastio")
    private String forecastio = "";
    public String forecastio() {
        return forecastio;
    }

    @JsonProperty("kinesis_producer")
    private KinesisProducerConfiguration kinesisProducerConfiguration;
    public KinesisProducerConfiguration kinesisProducerConfiguration() { return kinesisProducerConfiguration; }

    @JsonProperty("kinesis_consumer")
    private KinesisConsumerConfiguration kinesisConsumerConfiguration;
    public KinesisConsumerConfiguration kinesisConsumerConfiguration() { return kinesisConsumerConfiguration; }

    @JsonProperty("keys_management_service")
    private KMSConfiguration kmsConfiguration;
    public KMSConfiguration kmsConfiguration() { return this.kmsConfiguration; }

    @JsonProperty("s3_endpoint")
    private String s3Endpoint;
    public String s3Endpoint() { return s3Endpoint; }
}
