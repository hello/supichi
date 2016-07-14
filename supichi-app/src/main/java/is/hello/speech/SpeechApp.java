package is.hello.speech;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient;
import com.google.common.collect.ImmutableMap;
import com.hello.suripu.core.configuration.DynamoDBTableName;
import com.hello.suripu.core.db.FileInfoDAO;
import com.hello.suripu.core.db.FileManifestDAO;
import com.hello.suripu.core.db.FileManifestDynamoDB;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.coredw8.clients.AmazonDynamoDBClientFactory;
import com.hello.suripu.coredw8.clients.MessejiClient;
import com.hello.suripu.coredw8.clients.MessejiHttpClient;
import com.hello.suripu.coredw8.configuration.MessejiHttpClientConfiguration;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import io.dropwizard.Application;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import is.hello.speech.cli.WatsonTextToSpeech;
import is.hello.speech.clients.AsyncSpeechClient;
import is.hello.speech.clients.SpeechClientManaged;
import is.hello.speech.configuration.SpeechAppConfiguration;
import is.hello.speech.core.configuration.SQSConfiguration;
import is.hello.speech.core.configuration.WatsonConfiguration;
import is.hello.speech.core.db.DefaultResponseDAO;
import is.hello.speech.core.db.SpeechCommandDynamoDB;
import is.hello.speech.core.handlers.HandlerFactory;
import is.hello.speech.core.text2speech.Text2SpeechQueueConsumer;
import is.hello.speech.resources.v1.ParseResource;
import is.hello.speech.resources.v1.QueueMessageResource;
import is.hello.speech.resources.v1.UploadResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ExecutorService;

public class SpeechApp extends Application<SpeechAppConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpeechApp.class);

    public static void main(String[] args) throws Exception {
        new SpeechApp().run(args);
    }

    @Override
    public String getName() {
        return "hello-world";
    }

    @Override
    public void initialize(Bootstrap<SpeechAppConfiguration> bootstrap) {
        // nothing to do yet
        bootstrap.addCommand(new WatsonTextToSpeech());
    }


    @Override
    public void run(final SpeechAppConfiguration speechAppConfiguration, Environment environment) throws Exception {

//        final DBIFactory factory = new DBIFactory();
//        final DBI commonDB = factory.build(environment, speechAppConfiguration.getCommonDB(), "commonDB");
//
//        commonDB.registerArgumentFactory(new JodaArgumentFactory());
//        commonDB.registerContainerFactory(new OptionalContainerFactory());
//        commonDB.registerArgumentFactory(new PostgresIntegerArrayArgumentFactory());
//        commonDB.registerContainerFactory(new ImmutableListContainerFactory());
//        commonDB.registerContainerFactory(new ImmutableSetContainerFactory());
//
//        final DeviceDAO deviceDAO = commonDB.onDemand(DeviceDAO.class);
//        final FileInfoDAO fileInfoDAO = commonDB.onDemand(FileInfoDAO.class);


        final AWSCredentialsProvider awsCredentialsProvider = new DefaultAWSCredentialsProviderChain();
        final AmazonDynamoDBClientFactory dynamoDBClientFactory = AmazonDynamoDBClientFactory.create(awsCredentialsProvider, new ClientConfiguration(), speechAppConfiguration.dynamoDBConfiguration());

        final ImmutableMap<DynamoDBTableName, String> tableNames = speechAppConfiguration.dynamoDBConfiguration().tables();

        final AmazonDynamoDB speechCommandClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SLEEP_HMM);
        final SpeechCommandDynamoDB speechCommandDAO = new SpeechCommandDynamoDB(speechCommandClient, tableNames.get(DynamoDBTableName.SLEEP_HMM));

        final AmazonDynamoDB fileManifestDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.FILE_MANIFEST);
        final FileManifestDAO fileManifestDAO = new FileManifestDynamoDB(fileManifestDynamoDBClient, tableNames.get(DynamoDBTableName.FILE_MANIFEST));

        // for sleep sound handler
        final MessejiHttpClientConfiguration messejiHttpClientConfiguration = speechAppConfiguration.getMessejiHttpClientConfiguration();
        final MessejiClient messejiClient = MessejiHttpClient.create(
                new HttpClientBuilder(environment).using(messejiHttpClientConfiguration.getHttpClientConfiguration()).build("messeji"),
                messejiHttpClientConfiguration.getEndpoint());

        // TODO: add additional handler resources here

        final FileInfoDAO fileInfoDAO = null; // TODO: remove for google compute engine
        final HandlerFactory handlerFactory = HandlerFactory.create(
                speechCommandDAO,
                messejiClient,
                SleepSoundsProcessor.create(fileInfoDAO, fileManifestDAO));

        // setup SQS for QueueMessage API
        final SQSConfiguration sqsConfig = speechAppConfiguration.getSqsConfiguration();
        final int maxConnections = sqsConfig.getSqsMaxConnections();
        final AmazonSQSAsync sqsClient = new AmazonSQSBufferedAsyncClient(
                new AmazonSQSAsyncClient(awsCredentialsProvider, new ClientConfiguration()
                        .withMaxConnections(maxConnections)
                        .withConnectionTimeout(500)
                        .withMaxErrorRetry(3)));

        final Region region = Region.getRegion(Regions.US_EAST_1);
        sqsClient.setRegion(region);

        final String sqsQueueUrl = sqsConfig.getSqsQueueUrl();

        environment.jersey().register(new QueueMessageResource(sqsClient, sqsQueueUrl, sqsConfig));

        // set up watson
        final TextToSpeech watson = new TextToSpeech();
        final WatsonConfiguration watsonConfiguration = speechAppConfiguration.getWatsonConfiguration();
        watson.setUsernameAndPassword(watsonConfiguration.getUsername(), watsonConfiguration.getPassword());
        final Map<String, String> headers = ImmutableMap.of("X-Watson-Learning-Opt-Out", "true");
        watson.setDefaultHeaders(headers);

        // set up S3
        final ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.withConnectionTimeout(200); // in ms
        clientConfiguration.withMaxErrorRetry(1);
        final AmazonS3 amazonS3 = new AmazonS3Client(awsCredentialsProvider, clientConfiguration);

        // set up text2speech consumer manager
        final ExecutorService consumerExecutor = environment.lifecycle().executorService("queue_consumer")
                .minThreads(1)
                .maxThreads(2)
                .keepAliveTime(Duration.seconds(2L)).build();

        final String speechBucket = speechAppConfiguration.getSaveAudioConfiguration().getBucketName();
        final Text2SpeechQueueConsumer consumer = new Text2SpeechQueueConsumer(
                amazonS3, speechBucket,
                speechAppConfiguration.getSaveAudioConfiguration().getAudioPrefixRaw(),
                speechAppConfiguration.getSaveAudioConfiguration().getAudioPrefixCompressed(),
                watson, watsonConfiguration.getVoiceName(),
                sqsClient, sqsQueueUrl, speechAppConfiguration.getSqsConfiguration(),
                consumerExecutor);

        environment.lifecycle().manage(consumer);

        // Speech API
        final AWSCredentials awsCredentials = new AWSCredentials() {
            public String getAWSAccessKeyId() { return speechAppConfiguration.getS3Configuration().getAwsAccessKey(); }
            public String getAWSSecretKey() { return speechAppConfiguration.getS3Configuration().getAwsSecretKey(); }
        };

        final AsyncSpeechClient client = new AsyncSpeechClient(
                speechAppConfiguration.getGoogleAPIHost(),
                speechAppConfiguration.getGoogleAPIPort(),
                speechAppConfiguration.getAudioConfiguration());

        final SpeechClientManaged speechClientManaged = new SpeechClientManaged(client);
        environment.lifecycle().manage(speechClientManaged);

        final DefaultResponseDAO defaultResponseDAO = DefaultResponseDAO.create(amazonS3,
                String.format("%s/%s", speechBucket,
                        speechAppConfiguration.getSaveAudioConfiguration().getAudioPrefixCompressed()));

        environment.jersey().register(new UploadResource(amazonS3, speechAppConfiguration.getS3Configuration().getBucket(), client, handlerFactory, defaultResponseDAO));

        environment.jersey().register(new ParseResource());
    }
}
