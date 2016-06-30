package is.hello.speech.workers;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import io.dropwizard.Application;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import is.hello.speech.workers.framework.SQSConfiguration;
import is.hello.speech.workers.framework.WatsonConfiguration;
import is.hello.speech.workers.framework.WorkerConfiguration;
import is.hello.speech.workers.text2speech.Text2SpeechQueueConsumer;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;

/**
 * Created by ksg on 6/28/16
 */
public class SupichiWorker extends Application<WorkerConfiguration> {

    private final Logger LOGGER = LoggerFactory.getLogger(SupichiWorker.class);

    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        DateTimeZone.setDefault(DateTimeZone.UTC);
        new SupichiWorker().run(args);
    }

    @Override
    public void run(WorkerConfiguration workerConfiguration, Environment environment) throws Exception {

        final AWSCredentialsProvider provider = new DefaultAWSCredentialsProviderChain();
        final ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.withConnectionTimeout(200); // in ms
        clientConfiguration.withMaxErrorRetry(1);

        final AmazonS3 amazonS3 = new AmazonS3Client(provider, clientConfiguration);

        // setup SQS
        final SQSConfiguration sqsConfig = workerConfiguration.getSqsConfiguration();
        final int maxConnections = sqsConfig.getSqsMaxConnections();
        final AmazonSQSAsync sqsClient = new AmazonSQSBufferedAsyncClient(
                new AmazonSQSAsyncClient(provider, new ClientConfiguration()
                        .withMaxConnections(maxConnections)
                        .withConnectionTimeout(500)));

        final Region region = Region.getRegion(Regions.US_EAST_1);
        sqsClient.setRegion(region);

        // get SQS queue url
        final Optional<String> optionalSqsQueueUrl = this.getSqsQueueUrl(sqsClient, sqsConfig.getSqsQueueName());
        if (!optionalSqsQueueUrl.isPresent()) {
            LOGGER.error("key=suripu-queue error=no-sqs-queue-found queue-name={}", sqsConfig.getSqsQueueName());
            throw new Exception("Invalid queue name");
        }

        final String sqsQueueUrl = optionalSqsQueueUrl.get();


        // set up watson
        final TextToSpeech watson = new TextToSpeech();
        final WatsonConfiguration watsonConfiguration = workerConfiguration.getWatsonConfiguration();
        watson.setUsernameAndPassword(watsonConfiguration.getUsername(), watsonConfiguration.getPassword());
        final Map<String, String> headers = ImmutableMap.of("X-Watson-Learning-Opt-Out", "true");
        watson.setDefaultHeaders(headers);


        // set up consumer manager
        final ExecutorService consumerExecutor = environment.lifecycle().executorService("queue_consumer")
                .minThreads(1)
                .maxThreads(2)
                .keepAliveTime(Duration.seconds(2L)).build();

        final Text2SpeechQueueConsumer consumer = new Text2SpeechQueueConsumer(
                amazonS3, workerConfiguration.getSaveAudioConfiguration(),
                watson, watsonConfiguration.getVoiceName(),
                sqsClient, sqsQueueUrl, workerConfiguration.getSqsConfiguration(),
                consumerExecutor);

        environment.lifecycle().manage(consumer);

    }


    private Optional<String>  getSqsQueueUrl(final AmazonSQS client, final String queueName) {
        try {
            final List<String> queueUrls = client.listQueues().getQueueUrls();
            for (final String url : queueUrls) {
                if (url.contains(queueName)) {
                    LOGGER.debug("action=found-sqs-url value={}", url);
                    return Optional.of(url);
                }
            }
        }  catch (AmazonServiceException ase) {
            LOGGER.error("error=sqs-request-rejected reason={}", ase.getMessage()) ;
        } catch (AmazonClientException ace) {
            LOGGER.error("error=amazon-client-exception-internal-problem reason={}", ace.getMessage());
        }
        return Optional.absent();
    }
}
