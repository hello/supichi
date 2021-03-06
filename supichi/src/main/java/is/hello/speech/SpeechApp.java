package is.hello.speech;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kms.AWSKMSClient;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hello.suripu.core.ObjectGraphRoot;
import com.hello.suripu.core.configuration.DynamoDBTableName;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.db.AccountDAOImpl;
import com.hello.suripu.core.db.AccountLocationDAO;
import com.hello.suripu.core.db.AlarmDAODynamoDB;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.CalibrationDynamoDB;
import com.hello.suripu.core.db.DefaultModelEnsembleDAO;
import com.hello.suripu.core.db.DefaultModelEnsembleFromS3;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.FeatureExtractionModelsDAO;
import com.hello.suripu.core.db.FeatureExtractionModelsDAODynamoDB;
import com.hello.suripu.core.db.FeatureStore;
import com.hello.suripu.core.db.FeedbackDAO;
import com.hello.suripu.core.db.FileInfoDAO;
import com.hello.suripu.core.db.FileManifestDAO;
import com.hello.suripu.core.db.FileManifestDynamoDB;
import com.hello.suripu.core.db.HistoricalPairingDAO;
import com.hello.suripu.core.db.KeyStoreDynamoDB;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.db.OnlineHmmModelsDAO;
import com.hello.suripu.core.db.OnlineHmmModelsDAODynamoDB;
import com.hello.suripu.core.db.PairingDAO;
import com.hello.suripu.core.db.PillDataDAODynamoDB;
import com.hello.suripu.core.db.RingTimeHistoryDAODynamoDB;
import com.hello.suripu.core.db.SenseDataDAO;
import com.hello.suripu.core.db.SenseDataDAODynamoDB;
import com.hello.suripu.core.db.SleepScoreParametersDAO;
import com.hello.suripu.core.db.SleepScoreParametersDynamoDB;
import com.hello.suripu.core.db.SleepStatsDAODynamoDB;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.db.UserTimelineTestGroupDAO;
import com.hello.suripu.core.db.UserTimelineTestGroupDAOImpl;
import com.hello.suripu.core.db.colors.SenseColorDAO;
import com.hello.suripu.core.db.colors.SenseColorDAOSQLImpl;
import com.hello.suripu.core.db.util.JodaArgumentFactory;
import com.hello.suripu.core.db.util.PostgresIntegerArrayArgumentFactory;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.core.speech.KmsVault;
import com.hello.suripu.core.speech.SpeechResultIngestDAODynamoDB;
import com.hello.suripu.core.speech.interfaces.SpeechResultIngestDAO;
import com.hello.suripu.core.speech.interfaces.Vault;
import com.hello.suripu.coredropwizard.clients.AmazonDynamoDBClientFactory;
import com.hello.suripu.coredropwizard.clients.MessejiClient;
import com.hello.suripu.coredropwizard.clients.MessejiHttpClient;
import com.hello.suripu.coredropwizard.clients.TaimurainHttpClient;
import com.hello.suripu.coredropwizard.configuration.GraphiteConfiguration;
import com.hello.suripu.coredropwizard.configuration.MessejiHttpClientConfiguration;
import com.hello.suripu.coredropwizard.configuration.S3BucketConfiguration;
import com.hello.suripu.coredropwizard.configuration.TaimurainHttpClientConfiguration;
import com.hello.suripu.coredropwizard.configuration.TimelineAlgorithmConfiguration;
import com.hello.suripu.coredropwizard.db.SleepHmmDAODynamoDB;
import com.hello.suripu.coredropwizard.metrics.RegexMetricFilter;
import com.hello.suripu.coredropwizard.timeline.InstrumentedTimelineProcessor;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.maxmind.geoip2.DatabaseReader;
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
import is.hello.speech.core.configuration.KMSConfiguration;
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
import is.hello.speech.core.utils.GeoUtils;
import is.hello.speech.handler.AudioRequestHandler;
import is.hello.speech.handler.SignedBodyHandler;
import is.hello.speech.kinesis.KinesisData;
import is.hello.speech.kinesis.SpeechKinesisProducer;
import is.hello.speech.modules.RolloutSupichiModule;
import is.hello.speech.resources.demo.DemoUploadResource;
import is.hello.speech.resources.demo.QueueMessageResource;
import is.hello.speech.resources.v2.UploadResource;
import is.hello.speech.utils.S3ResponseBuilder;
import is.hello.speech.utils.WatsonResponseBuilder;
import is.hello.supichi.api.Speech;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SpeechApp extends Application<SpeechAppConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpeechApp.class);

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
        final DBI commonDB = factory.build(environment, speechAppConfiguration.commonDB(), "commonDB");

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

        // set up S3
        final ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.withConnectionTimeout(200); // in ms
        clientConfiguration.withMaxErrorRetry(1);
        final AmazonS3 amazonS3 = new AmazonS3Client(awsCredentialsProvider, clientConfiguration);
        amazonS3.setRegion(Region.getRegion(Regions.US_EAST_1));
        amazonS3.setEndpoint(speechAppConfiguration.s3Endpoint());

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

        final AmazonDynamoDB dynamoDBSleepStatsClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SLEEP_STATS);
        final SleepStatsDAODynamoDB sleepStatsDAO = new SleepStatsDAODynamoDB(dynamoDBSleepStatsClient, tableNames.get(DynamoDBTableName.SLEEP_STATS), speechAppConfiguration.sleepStatsVersion());

        final AmazonDynamoDB featuresDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.FEATURES);
        final FeatureStore featureStore = new FeatureStore(featuresDynamoDBClient, tableNames.get(DynamoDBTableName.FEATURES), "prod");

        final RolloutSupichiModule module = new RolloutSupichiModule(featureStore, 30);
        ObjectGraphRoot.getInstance().init(module);

        // for sleep sound handler
        final MessejiHttpClientConfiguration messejiHttpClientConfiguration = speechAppConfiguration.messejiHttpClientConfiguration();
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

        final InstrumentedTimelineProcessor timelineProcessor = timelineProcessor(environment,
                speechAppConfiguration,
                dynamoDBClientFactory,
                commonDB,
                amazonS3,
                deviceDataDAODynamoDB,
                calibrationDAO,
                senseColorDAO,
                deviceDAO,
                sleepStatsDAO);

        Optional<DatabaseReader> geoIPDatabase = GeoUtils.geoIPDatabase();

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
                mergedUserInfoDynamoDB,
                sleepStatsDAO,
                timelineProcessor,
                geoIPDatabase
        );

        final HandlerExecutor handlerExecutor = new RegexAnnotationsHandlerExecutor(timeZoneHistoryDAODynamoDB) //new RegexHandlerExecutor()
                .register(HandlerType.ALARM, handlerFactory.alarmHandler())
                .register(HandlerType.WEATHER, handlerFactory.weatherHandler())
                .register(HandlerType.SLEEP_SOUNDS, handlerFactory.sleepSoundHandler())
                .register(HandlerType.ROOM_CONDITIONS, handlerFactory.roomConditionsHandler())
                .register(HandlerType.TIME_REPORT, handlerFactory.timeHandler())
                .register(HandlerType.TRIVIA, handlerFactory.triviaHandler())
                .register(HandlerType.TIMELINE, handlerFactory.timelineHandler())
                .register(HandlerType.HUE, handlerFactory.hueHandler(speechAppConfiguration.expansionConfiguration().hueAppName()))
                .register(HandlerType.NEST, handlerFactory.nestHandler())
                .register(HandlerType.ALEXA, handlerFactory.alexaHandler())
                .register(HandlerType.SLEEP_SUMMARY, handlerFactory.sleepSummaryHandler());

        // metrics
        if (speechAppConfiguration.metricsEnabled()) {
            final GraphiteConfiguration graphiteConfig = speechAppConfiguration.graphite();
            final String graphiteHostName = graphiteConfig.getHost();
            final String apiKey = graphiteConfig.getApiKey();
            final Integer interval = graphiteConfig.getReportingIntervalInSeconds();

            final String env = (speechAppConfiguration.debug()) ? "dev" : "prod";
            final String prefix = String.format("%s.%s.supichi", apiKey, env);

            final ImmutableList<String> metrics = ImmutableList.copyOf(graphiteConfig.getIncludeMetrics());
            final RegexMetricFilter metricFilter = new RegexMetricFilter(metrics);

            final Graphite graphite = new Graphite(new InetSocketAddress(graphiteHostName, 2003));

            final GraphiteReporter reporter = GraphiteReporter.forRegistry(environment.metrics())
                    .prefixedWith(prefix)
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .filter(metricFilter)
                    .build(graphite);
            reporter.start(interval, TimeUnit.SECONDS);

            LOGGER.info("info=metrics-enabled.");
        } else {
            LOGGER.warn("warning=metrics-not-enabled.");
        }

        LOGGER.warn("DEBUG_MODE={}", speechAppConfiguration.debug());


        // setup SQS for QueueMessage API
        final SQSConfiguration sqsConfig = speechAppConfiguration.sqsConfiguration();
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
        final WatsonConfiguration watsonConfiguration = speechAppConfiguration.watsonConfiguration();
        watson.setUsernameAndPassword(watsonConfiguration.getUsername(), watsonConfiguration.getPassword());
        final Map<String, String> headers = ImmutableMap.of("X-Watson-Learning-Opt-Out", "true");
        watson.setDefaultHeaders(headers);

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
                    sqsClient, sqsQueueUrl, speechAppConfiguration.sqsConfiguration(),
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
//        final KinesisConsumerConfiguration kinesisConsumerConfiguration = speechAppConfiguration.kinesisConsumerConfiguration();
//        final String workerId = InetAddress.getLocalHost().getCanonicalHostName();
//        final InitialPositionInStream initialPositionInStream = kinesisConsumerConfiguration.trimHorizon() ? InitialPositionInStream.TRIM_HORIZON : InitialPositionInStream.LATEST;
//
//        final KinesisClientLibConfiguration kinesisClientLibConfiguration = new KinesisClientLibConfiguration(
//                kinesisConsumerConfiguration.appName(),
//                kinesisConsumerConfiguration.streams().get(kinesisStream),
//                awsCredentialsProvider,
//                workerId)
//                .withKinesisEndpoint(kinesisConsumerConfiguration.endpoint())
//                .withMaxRecords(kinesisConsumerConfiguration.maxRecord())
//                .withInitialPositionInStream(initialPositionInStream);
//
//        final ExecutorService scheduledKinesisConsumer = environment.lifecycle().executorService("kinesis_consumer")
//                .minThreads(1)
//                .maxThreads(2)
//                .keepAliveTime(Duration.seconds(2L)).build();

//        final String senseUploadBucket = String.format("%s/%s",
//                speechAppConfiguration.senseUploadAudioConfiguration().getBucketName(),
//                speechAppConfiguration.senseUploadAudioConfiguration().getAudioPrefix());
//
//        final Vault kmsVault = new KmsVault(awskmsClient, kmsConfig.kmsKeys().uuid());
//
//        final AmazonDynamoDB speechTimelineClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SPEECH_TIMELINE);
//        final SpeechTimelineIngestDAO speechTimelineIngestDAO = SpeechTimelineIngestDAODynamoDB.create(speechTimelineClient, tableNames.get(DynamoDBTableName.SPEECH_TIMELINE), kmsVault);
//
//        final SSEAwsKeyManagementParams s3SSEKey = new SSEAwsKeyManagementParams(kmsConfig.kmsKeys().audio());
//
//        final SpeechKinesisConsumer speechKinesisConsumer = new SpeechKinesisConsumer(kinesisClientLibConfiguration,
//                scheduledKinesisConsumer,
//                amazonS3,
//                senseUploadBucket,
//                s3SSEKey,
//                speechTimelineIngestDAO,
//                speechResultIngestDAO);
//
//        environment.lifecycle().manage(speechKinesisConsumer);


        // set up speech client
        final SpeechClient client = new SpeechClient(
                speechAppConfiguration.googleAPIHost(),
                speechAppConfiguration.googleAPIPort(),
                speechAppConfiguration.audioConfiguration());

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
                client, signedBodyHandler, handlerExecutor, deviceDAO,
                speechKinesisProducer, responseBuilders, handlersToBuilders, environment.metrics()
        );

        environment.jersey().register(new DemoUploadResource(audioRequestHandler));
        environment.jersey().register(new UploadResource(audioRequestHandler));

    }

    private InstrumentedTimelineProcessor timelineProcessor(final Environment environment,
                                                            final SpeechAppConfiguration speechAppConfiguration,
                                                            final AmazonDynamoDBClientFactory dynamoDBClientFactory,
                                                            final DBI commonDB,
                                                            final AmazonS3 amazonS3,
                                                            final DeviceDataDAODynamoDB deviceDataDAODynamoDB,
                                                            final CalibrationDAO calibrationDAO,
                                                            final SenseColorDAO senseColorDAO,
                                                            final DeviceDAO deviceDAO,
                                                            final SleepStatsDAODynamoDB sleepStatsDAO) {

        final ImmutableMap<DynamoDBTableName, String> tableNames = speechAppConfiguration.dynamoDBConfiguration().tables();

        // ALL THE THINGS NEEDED BY TimelineProcessor
        final AmazonDynamoDB pillDataDAODynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.PILL_DATA);
        final PillDataDAODynamoDB pillDataDAODynamoDB = new PillDataDAODynamoDB(pillDataDAODynamoDBClient, tableNames.get(DynamoDBTableName.PILL_DATA));

        final AmazonDynamoDB ringTimeHistoryDynamoDBClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.RING_TIME_HISTORY);
        final RingTimeHistoryDAODynamoDB ringTimeHistoryDAODynamoDB = new RingTimeHistoryDAODynamoDB(ringTimeHistoryDynamoDBClient, tableNames.get(DynamoDBTableName.RING_TIME_HISTORY));

        final AmazonDynamoDB sleepHmmDynamoDbClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SLEEP_HMM);
        final SleepHmmDAODynamoDB sleepHmmDAODynamoDB = new SleepHmmDAODynamoDB(sleepHmmDynamoDbClient, tableNames.get(DynamoDBTableName.SLEEP_HMM));

        /* Individual models for users  */
        final AmazonDynamoDB onlineHmmModelsDb = dynamoDBClientFactory.getForTable(DynamoDBTableName.ONLINE_HMM_MODELS);
        final OnlineHmmModelsDAO onlineHmmModelsDAO = OnlineHmmModelsDAODynamoDB.create(onlineHmmModelsDb, tableNames.get(DynamoDBTableName.ONLINE_HMM_MODELS));

        /* Models for feature extraction layer */
        final AmazonDynamoDB featureExtractionModelsDb = dynamoDBClientFactory.getForTable(DynamoDBTableName.FEATURE_EXTRACTION_MODELS);
        final FeatureExtractionModelsDAO featureExtractionDAO = new FeatureExtractionModelsDAODynamoDB(featureExtractionModelsDb, tableNames.get(DynamoDBTableName.FEATURE_EXTRACTION_MODELS));

        final AmazonDynamoDB sleepScoreParametersClient = dynamoDBClientFactory.getForTable(DynamoDBTableName.SLEEP_SCORE_PARAMETERS);
        final SleepScoreParametersDAO sleepScoreParametersDAO = new SleepScoreParametersDynamoDB(sleepScoreParametersClient, tableNames.get(DynamoDBTableName.SLEEP_SCORE_PARAMETERS));


        final FeedbackDAO feedbackDAO = commonDB.onDemand(FeedbackDAO.class);
        final AccountDAO accountDAO = commonDB.onDemand(AccountDAOImpl.class);
        final PairingDAO pairingDAO = new HistoricalPairingDAO(deviceDAO,deviceDataDAODynamoDB);
        final UserTimelineTestGroupDAO userTimelineTestGroupDAO = commonDB.onDemand(UserTimelineTestGroupDAOImpl.class);

        final SenseDataDAO senseDataDAO = new SenseDataDAODynamoDB(pairingDAO, deviceDataDAODynamoDB, senseColorDAO, calibrationDAO);

        /* Default model ensemble for all users  */
        final S3BucketConfiguration timelineModelEnsemblesConfig = speechAppConfiguration.timelineModelEnsembles();
        final S3BucketConfiguration seedModelConfig = speechAppConfiguration.timelineSeedModel();

        final DefaultModelEnsembleDAO defaultModelEnsembleDAO = DefaultModelEnsembleFromS3.create(
                amazonS3,
                timelineModelEnsemblesConfig.getBucket(),
                timelineModelEnsemblesConfig.getKey(),
                seedModelConfig.getBucket(),
                seedModelConfig.getKey()
        );

        final TaimurainHttpClientConfiguration taimurainHttpClientConfiguration = speechAppConfiguration.taimurainClient();
        final TaimurainHttpClient taimurainHttpClient = TaimurainHttpClient.create(
                new HttpClientBuilder(environment).using(taimurainHttpClientConfiguration.getHttpClientConfiguration()).build("taimurain"),
                taimurainHttpClientConfiguration.getEndpoint());


        final TimelineAlgorithmConfiguration timelineAlgorithmConfiguration = speechAppConfiguration.timelineAlgorithm();

        return InstrumentedTimelineProcessor.createTimelineProcessor(
                pillDataDAODynamoDB,
                deviceDAO,
                deviceDataDAODynamoDB,
                ringTimeHistoryDAODynamoDB,
                feedbackDAO,
                sleepHmmDAODynamoDB,
                accountDAO,
                sleepStatsDAO,
                senseDataDAO,
                onlineHmmModelsDAO,
                featureExtractionDAO,
                defaultModelEnsembleDAO,
                userTimelineTestGroupDAO,
                sleepScoreParametersDAO,
                taimurainHttpClient,
                timelineAlgorithmConfiguration,
                environment.metrics());
    }
}
