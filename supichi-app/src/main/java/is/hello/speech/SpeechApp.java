package is.hello.speech;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.common.collect.ImmutableMap;
import com.hello.suripu.core.configuration.DynamoDBTableName;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.FileInfoDAO;
import com.hello.suripu.core.db.FileManifestDAO;
import com.hello.suripu.core.db.FileManifestDynamoDB;
import com.hello.suripu.core.db.util.JodaArgumentFactory;
import com.hello.suripu.core.db.util.PostgresIntegerArrayArgumentFactory;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.coredw8.clients.AmazonDynamoDBClientFactory;
import com.hello.suripu.coredw8.clients.MessejiClient;
import com.hello.suripu.coredw8.clients.MessejiHttpClient;
import com.hello.suripu.coredw8.configuration.MessejiHttpClientConfiguration;
import io.dropwizard.Application;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.jdbi.ImmutableListContainerFactory;
import io.dropwizard.jdbi.ImmutableSetContainerFactory;
import io.dropwizard.jdbi.OptionalContainerFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import is.hello.speech.clients.AsyncSpeechClient;
import is.hello.speech.clients.SpeechClientManaged;
import is.hello.speech.configuration.SpeechAppConfiguration;
import is.hello.speech.core.db.SpeechCommandDynamoDB;
import is.hello.speech.core.handlers.HandlerFactory;
import is.hello.speech.resources.v1.UploadResource;
import org.skife.jdbi.v2.DBI;

public class SpeechApp extends Application<SpeechAppConfiguration> {

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
        final FileInfoDAO fileInfoDAO = commonDB.onDemand(FileInfoDAO.class);


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

        final HandlerFactory handlerFactory = HandlerFactory.create(
                speechCommandDAO,
                messejiClient,
                SleepSoundsProcessor.create(fileInfoDAO, fileManifestDAO));


        final AWSCredentials awsCredentials = new AWSCredentials() {
            public String getAWSAccessKeyId() { return speechAppConfiguration.getS3Configuration().getAwsAccessKey(); }
            public String getAWSSecretKey() { return speechAppConfiguration.getS3Configuration().getAwsSecretKey(); }
        };


        final AmazonS3 amazonS3 = new AmazonS3Client(awsCredentials);

        final AsyncSpeechClient client = new AsyncSpeechClient(
                speechAppConfiguration.getGoogleAPIHost(),
                speechAppConfiguration.getGoogleAPIPort(),
                speechAppConfiguration.getAudioConfiguration());

        final SpeechClientManaged speechClientManaged = new SpeechClientManaged(client);

        environment.lifecycle().manage(speechClientManaged);
        environment.jersey().register(new UploadResource(amazonS3, speechAppConfiguration.getS3Configuration().getBucket(), client, handlerFactory));
    }
}
