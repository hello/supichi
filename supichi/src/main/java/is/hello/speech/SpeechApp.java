package is.hello.speech;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kms.AWSKMSClient;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.SSEAwsKeyManagementParams;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hello.suripu.core.configuration.DynamoDBTableName;
import com.hello.suripu.core.db.AccountLocationDAO;
import com.hello.suripu.core.db.AlarmDAODynamoDB;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.CalibrationDynamoDB;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.FileInfoDAO;
import com.hello.suripu.core.db.FileManifestDAO;
import com.hello.suripu.core.db.FileManifestDynamoDB;
import com.hello.suripu.core.db.KeyStoreDynamoDB;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.db.colors.SenseColorDAO;
import com.hello.suripu.core.db.colors.SenseColorDAOSQLImpl;
import com.hello.suripu.core.db.util.JodaArgumentFactory;
import com.hello.suripu.core.db.util.PostgresIntegerArrayArgumentFactory;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.core.speech.KmsVault;
import com.hello.suripu.core.speech.SpeechResultIngestDAODynamoDB;
import com.hello.suripu.core.speech.SpeechTimelineIngestDAODynamoDB;
import com.hello.suripu.core.speech.interfaces.SpeechResultIngestDAO;
import com.hello.suripu.core.speech.interfaces.SpeechTimelineIngestDAO;
import com.hello.suripu.core.speech.interfaces.Vault;
import com.hello.suripu.coredropwizard.clients.AmazonDynamoDBClientFactory;
import com.hello.suripu.coredropwizard.clients.MessejiClient;
import com.hello.suripu.coredropwizard.clients.MessejiHttpClient;
import com.hello.suripu.coredropwizard.configuration.MessejiHttpClientConfiguration;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import io.dropwizard.Application;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.jdbi.ImmutableListContainerFactory;
import io.dropwizard.jdbi.ImmutableSetContainerFactory;
import io.dropwizard.jdbi.OptionalContainerFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import is.hello.gaibu.core.db.ExpansionDataDAO;
import is.hello.gaibu.core.db.ExpansionsDAO;
import is.hello.gaibu.core.db.ExternalTokenDAO;
import is.hello.gaibu.core.stores.PersistentExpansionDataStore;
import is.hello.gaibu.core.stores.PersistentExpansionStore;
import is.hello.gaibu.core.stores.PersistentExternalTokenStore;
import is.hello.speech.cli.WatsonTextToSpeech;
import is.hello.speech.clients.SpeechClient;
import is.hello.speech.clients.SpeechClientManaged;
import is.hello.speech.configuration.SpeechAppConfiguration;
import is.hello.speech.core.api.Speech;
import is.hello.speech.core.configuration.KMSConfiguration;
import is.hello.speech.core.configuration.KinesisConsumerConfiguration;
import is.hello.speech.core.configuration.KinesisProducerConfiguration;
import is.hello.speech.core.configuration.KinesisStream;
import is.hello.speech.core.configuration.SQSConfiguration;
import is.hello.speech.core.configuration.WatsonConfiguration;
import is.hello.speech.core.db.SpeechCommandDynamoDB;
import is.hello.speech.core.executors.HandlerExecutor;
import is.hello.speech.core.executors.RegexAnnotationsHandlerExecutor;
import is.hello.speech.core.handlers.HandlerFactory;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.response.SupichiResponseBuilder;
import is.hello.speech.core.response.SupichiResponseType;
import is.hello.speech.core.text2speech.Text2SpeechQueueConsumer;
import is.hello.speech.handler.AudioRequestHandler;
import is.hello.speech.handler.SignedBodyHandler;
import is.hello.speech.kinesis.KinesisData;
import is.hello.speech.kinesis.SpeechKinesisConsumer;
import is.hello.speech.kinesis.SpeechKinesisProducer;
import is.hello.speech.resources.demo.DemoUploadResource;
import is.hello.speech.resources.demo.QueueMessageResource;
import is.hello.speech.resources.v2.UploadResource;
import is.hello.speech.utils.S3ResponseBuilder;
import is.hello.speech.utils.WatsonResponseBuilder;
import org.skife.jdbi.v2.DBI;

import java.net.InetAddress;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public class SpeechApp extends Application<SpeechAppConfiguration> {

    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
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

        final DBIFactory factory = new DBIFactory();
        final DBI commonDB = factory.build(environment, speechAppConfiguration.getCommonDB(), "commonDB");

        commonDB.registerArgumentFactory(new JodaArgumentFactory());
        commonDB.registerContainerFactory(new OptionalContainerFactory());
        commonDB.registerArgumentFactory(new PostgresIntegerArrayArgumentFactory());
        commonDB.registerContainerFactory(new ImmutableListContainerFactory());
        commonDB.registerContainerFactory(new ImmutableSetContainerFactory());

        final DeviceDAO deviceDAO = commonDB.onDemand(DeviceDAO.class);
        final SenseColorDAO senseColorDAO = commonDB.onDemand(SenseColorDAOSQLImpl.class);
        final AccountLocationDAO accountLocationDAO = commonDB.onDemand(AccountLocationDAO.class);

//        final FileInfoDAO fileInfoDAO = commonDB.onDemand(FileInfoDAO.class);


        final AWSCredentialsProvider awsCredentialsProvider = new DefaultAWSCredentialsProviderChain();
        final AmazonDynamoDBClientFactory dynamoDBClientFactory = AmazonDynamoDBClientFactory.create(awsCredentialsProvider, speechAppConfiguration.dynamoDBConfiguration());

        final ImmutableMap<DynamoDBTableName, String> tableNames = speechAppConfiguration.dynamoDBConfiguration().tables();

        final AmazonDynamoDB speechCommandClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SPEECH_COMMANDS);
        final SpeechCommandDynamoDB speechCommandDAO = new SpeechCommandDynamoDB(speechCommandClient, tableNames.get(DynamoDBTableName.SLEEP_HMM));

        final AmazonDynamoDB fileManifestDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.FILE_MANIFEST);
        final FileManifestDAO fileManifestDAO = new FileManifestDynamoDB(fileManifestDynamoDBClient, tableNames.get(DynamoDBTableName.FILE_MANIFEST));

        final AmazonDynamoDB deviceDataClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.DEVICE_DATA);
        final DeviceDataDAODynamoDB deviceDataDAODynamoDB = new DeviceDataDAODynamoDB(deviceDataClient, tableNames.get(DynamoDBTableName.DEVICE_DATA));

        final AmazonDynamoDB calibrationDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.CALIBRATION);
        final CalibrationDAO calibrationDAO = CalibrationDynamoDB.create(calibrationDynamoDBClient, tableNames.get(DynamoDBTableName.CALIBRATION));

        final AmazonDynamoDB timezoneClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.TIMEZONE_HISTORY);
        final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB = new TimeZoneHistoryDAODynamoDB(timezoneClient, tableNames.get(DynamoDBTableName.TIMEZONE_HISTORY));

        final AmazonDynamoDB speechResultsClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SPEECH_RESULTS);
       final SpeechResultIngestDAO speechResultIngestDAO = SpeechResultIngestDAODynamoDB.create(speechResultsClient, tableNames.get(DynamoDBTableName.SPEECH_RESULTS));

        final AmazonDynamoDB keystoreClient= dynamoDBClientFactory.getForTable(DynamoDBTableName.SENSE_KEY_STORE);
        final KeyStoreDynamoDB keystore = new KeyStoreDynamoDB(keystoreClient, tableNames.get(DynamoDBTableName.SENSE_KEY_STORE), "hello".getBytes(),10);

        final AmazonDynamoDB alarmClient= dynamoDBClientFactory.getForTable(DynamoDBTableName.ALARM);
        final AlarmDAODynamoDB alarmDAODynamoDB = new AlarmDAODynamoDB(alarmClient, tableNames.get(DynamoDBTableName.ALARM));

        final AmazonDynamoDB mergeInfoClient= dynamoDBClientFactory.getForTable(DynamoDBTableName.ALARM_INFO);
        final MergedUserInfoDynamoDB mergedUserInfoDynamoDB = new MergedUserInfoDynamoDB(mergeInfoClient, tableNames.get(DynamoDBTableName.ALARM_INFO));

        // for sleep sound handler
        final MessejiHttpClientConfiguration messejiHttpClientConfiguration = speechAppConfiguration.getMessejiHttpClientConfiguration();
        final MessejiClient messejiClient = MessejiHttpClient.create(
                new HttpClientBuilder(environment).using(messejiHttpClientConfiguration.getHttpClientConfiguration()).build("messeji"),
                messejiHttpClientConfiguration.getEndpoint());


        // TODO: add additional handler resources here
      // set up KMS for timeline encryption
        final KMSConfiguration kmsConfig = speechAppConfiguration.kmsConfiguration();
        final AWSKMSClient awskmsClient = new AWSKMSClient(awsCredentialsProvider);
        awskmsClient.setEndpoint(kmsConfig.endpoint());

        final Vault tokenKMSVault = new KmsVault(awskmsClient, kmsConfig.kmsKeys().token());

        final FileInfoDAO fileInfoDAO = null; // TODO: remove for google compute engine

        final ExpansionsDAO expansionsDAO = commonDB.onDemand(ExpansionsDAO.class);
        final PersistentExpansionStore expansionStore = new PersistentExpansionStore(expansionsDAO);

        final ExternalTokenDAO externalTokenDAO = commonDB.onDemand(ExternalTokenDAO.class);
        final PersistentExternalTokenStore externalTokenStore = new PersistentExternalTokenStore(externalTokenDAO, expansionStore);

        final ExpansionDataDAO expansionsDataDAO = commonDB.onDemand(ExpansionDataDAO.class);
        final PersistentExpansionDataStore expansionsDataStore = new PersistentExpansionDataStore(expansionsDataDAO);

        final HandlerFactory handlerFactory = HandlerFactory.create(
                speechCommandDAO,
                messejiClient,
                SleepSoundsProcessor.create(fileInfoDAO, fileManifestDAO),
                deviceDataDAODynamoDB,
                deviceDAO,
                senseColorDAO,
                calibrationDAO,
                timeZoneHistoryDAODynamoDB,
                speechAppConfiguration.forecastio(),
                accountLocationDAO,
                externalTokenStore,
                expansionStore,
                expansionsDataStore,
                tokenKMSVault,
                alarmDAODynamoDB,
                mergedUserInfoDynamoDB
        );

        final HandlerExecutor handlerExecutor = new RegexAnnotationsHandlerExecutor(timeZoneHistoryDAODynamoDB) //new RegexHandlerExecutor()
                .register(HandlerType.ALARM, handlerFactory.alarmHandler())
                .register(HandlerType.WEATHER, handlerFactory.weatherHandler())
                .register(HandlerType.SLEEP_SOUNDS, handlerFactory.sleepSoundHandler())
                .register(HandlerType.ROOM_CONDITIONS, handlerFactory.roomConditionsHandler())
                .register(HandlerType.TIME_REPORT, handlerFactory.timeHandler())
                .register(HandlerType.TRIVIA, handlerFactory.triviaHandler())
                .register(HandlerType.TIMELINE, handlerFactory.timelineHandler())
                .register(HandlerType.HUE, handlerFactory.hueHandler())
                .register(HandlerType.NEST, handlerFactory.nestHandler())
                .register(HandlerType.ALEXA, handlerFactory.alexaHandler());


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
        amazonS3.setRegion(Region.getRegion(Regions.US_EAST_1));
        amazonS3.setEndpoint(speechAppConfiguration.s3Endpoint());

        // set up Text2speech Consumer
        final ExecutorService queueConsumerExecutor = environment.lifecycle().executorService("text2speech_queue_consumer")
                .minThreads(1)
                .maxThreads(2)
                .keepAliveTime(Duration.seconds(2L)).build();

        final String speechBucket = speechAppConfiguration.watsonAudioConfiguration().getBucketName();
        if(speechAppConfiguration.consumerEnabled()) {
            final Text2SpeechQueueConsumer text2SpeechQueueConsumer = new Text2SpeechQueueConsumer(
                    amazonS3, speechBucket,
                    speechAppConfiguration.watsonAudioConfiguration().getAudioPrefixRaw(),
                    speechAppConfiguration.watsonAudioConfiguration().getAudioPrefix(),
                    watson, watsonConfiguration.getVoiceName(),
                    sqsClient, sqsQueueUrl, speechAppConfiguration.getSqsConfiguration(),
                    queueConsumerExecutor);

            environment.lifecycle().manage(text2SpeechQueueConsumer);
        }

        // set up Kinesis Producer
        final KinesisStream kinesisStream = KinesisStream.SPEECH_RESULT;

        final KinesisProducerConfiguration kinesisProducerConfiguration = speechAppConfiguration.kinesisProducerConfiguration();
        final com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration kplConfig = new com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration()
                .setRegion(kinesisProducerConfiguration.region())
                .setCredentialsProvider(awsCredentialsProvider)
                .setMaxConnections(kinesisProducerConfiguration.maxConnections())
                .setRequestTimeout(kinesisProducerConfiguration.requstTimeout())
                .setRecordMaxBufferedTime(kinesisProducerConfiguration.recordMaxBufferedTime())
                .setCredentialsRefreshDelay(1000L);

        final ExecutorService kinesisExecutor = environment.lifecycle().executorService("kinesis_producer")
                .minThreads(1)
                .maxThreads(2)
                .keepAliveTime(Duration.seconds(2L)).build();

        final ScheduledExecutorService kinesisMetricsExecutor = environment.lifecycle().scheduledExecutorService("kinesis_producer_metrics").threads(1).build();

        final KinesisProducer kinesisProducer = new KinesisProducer(kplConfig);
        final String kinesisStreamName = kinesisProducerConfiguration.streams().get(kinesisStream);
        final BlockingQueue<KinesisData> kinesisEvents = new ArrayBlockingQueue<>(kinesisProducerConfiguration.queueSize());
        final SpeechKinesisProducer speechKinesisProducer = new SpeechKinesisProducer(kinesisStreamName, kinesisEvents, kinesisProducer, kinesisExecutor, kinesisMetricsExecutor);
        environment.lifecycle().manage(speechKinesisProducer);


        // set up Kinesis Consumer
        final KinesisConsumerConfiguration kinesisConsumerConfiguration = speechAppConfiguration.kinesisConsumerConfiguration();
        final String workerId = InetAddress.getLocalHost().getCanonicalHostName();
        final InitialPositionInStream initialPositionInStream = kinesisConsumerConfiguration.trimHorizon() ? InitialPositionInStream.TRIM_HORIZON : InitialPositionInStream.LATEST;

        final KinesisClientLibConfiguration kinesisClientLibConfiguration = new KinesisClientLibConfiguration(
                kinesisConsumerConfiguration.appName(),
                kinesisConsumerConfiguration.streams().get(kinesisStream),
                awsCredentialsProvider,
                workerId)
                .withKinesisEndpoint(kinesisConsumerConfiguration.endpoint())
                .withMaxRecords(kinesisConsumerConfiguration.maxRecord())
                .withInitialPositionInStream(initialPositionInStream);

        final ExecutorService scheduledKinesisConsumer = environment.lifecycle().executorService("kinesis_consumer")
                .minThreads(1)
                .maxThreads(2)
                .keepAliveTime(Duration.seconds(2L)).build();

        final String senseUploadBucket = String.format("%s/%s",
                speechAppConfiguration.senseUploadAudioConfiguration().getBucketName(),
                speechAppConfiguration.senseUploadAudioConfiguration().getAudioPrefix());

        final Vault kmsVault = new KmsVault(awskmsClient, kmsConfig.kmsKeys().uuid());

        final AmazonDynamoDB speechTimelineClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SPEECH_TIMELINE);
        final SpeechTimelineIngestDAO speechTimelineIngestDAO = SpeechTimelineIngestDAODynamoDB.create(speechTimelineClient, tableNames.get(DynamoDBTableName.SPEECH_TIMELINE), kmsVault);

        final SSEAwsKeyManagementParams s3SSEKey = new SSEAwsKeyManagementParams(kmsConfig.kmsKeys().audio());

        final SpeechKinesisConsumer speechKinesisConsumer = new SpeechKinesisConsumer(kinesisClientLibConfiguration,
                scheduledKinesisConsumer,
                amazonS3,
                senseUploadBucket,
                s3SSEKey,
                speechTimelineIngestDAO,
                speechResultIngestDAO);

        environment.lifecycle().manage(speechKinesisConsumer);


        // set up speech client
        final SpeechClient client = new SpeechClient(
                speechAppConfiguration.getGoogleAPIHost(),
                speechAppConfiguration.getGoogleAPIPort(),
                speechAppConfiguration.getAudioConfiguration());

        final SpeechClientManaged speechClientManaged = new SpeechClientManaged(client);
        environment.lifecycle().manage(speechClientManaged);

        // Map Eq profile to s3 bucket/path
        final String s3ResponseBucket = String.format("%s/%s", speechBucket, speechAppConfiguration.watsonAudioConfiguration().getAudioPrefix());
        final String s3ResponseBucketNoEq = String.format("%s/voice/watson-text2speech/16k", speechBucket);
        final Map<Speech.Equalizer, String> eqMap = ImmutableMap.<Speech.Equalizer, String>builder()
                .put(Speech.Equalizer.SENSE_ONE, s3ResponseBucket)
                .put(Speech.Equalizer.NONE, s3ResponseBucketNoEq)
                .build();

        final S3ResponseBuilder s3ResponseBuilder = new S3ResponseBuilder(amazonS3, eqMap, "WATSON", watsonConfiguration.getVoiceName());
        final WatsonResponseBuilder watsonResponseBuilder = new WatsonResponseBuilder(watson, watsonConfiguration.getVoiceName());
        final SignedBodyHandler signedBodyHandler = new SignedBodyHandler(keystore);

        final Map<SupichiResponseType, SupichiResponseBuilder> responseBuilders = Maps.newHashMap();
        responseBuilders.put(SupichiResponseType.S3, s3ResponseBuilder);
        responseBuilders.put(SupichiResponseType.WATSON, watsonResponseBuilder);

        final Map<HandlerType, SupichiResponseType> handlersToBuilders = handlerExecutor.responseBuilders();

        final AudioRequestHandler audioRequestHandler = new AudioRequestHandler(
                client, signedBodyHandler, handlerExecutor, deviceDAO, speechKinesisProducer, responseBuilders, handlersToBuilders
        );

        environment.jersey().register(new DemoUploadResource(audioRequestHandler));
        environment.jersey().register(new UploadResource(audioRequestHandler));

    }
}
