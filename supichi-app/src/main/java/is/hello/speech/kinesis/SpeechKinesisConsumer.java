package is.hello.speech.kinesis;

import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker;
import com.amazonaws.services.kms.AWSKMSClient;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.SSEAwsKeyManagementParams;
import com.hello.suripu.core.speech.SpeechResultDAODynamoDB;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by ksg on 8/11/16
 */
public class SpeechKinesisConsumer implements Managed {
    private final Logger LOGGER = LoggerFactory.getLogger(SpeechKinesisConsumer.class);

    private final KinesisClientLibConfiguration clientConfiguration;
    private final ScheduledExecutorService consumerExecutor;
    private final long scheduledIntervalMinutes;

    private final AmazonS3 s3;
    private final String s3Bucket;

    private final AWSKMSClient awskmsClient;
    private final String kmsUUIDKey;
    private final SSEAwsKeyManagementParams s3SSEKey;

    private final SpeechResultDAODynamoDB speechResultDAODynamoDB;

    private boolean isRunning = false;

    public SpeechKinesisConsumer(final KinesisClientLibConfiguration clientConfiguration,
                                 final ScheduledExecutorService consumerExecutor,
                                 final long scheduledIntervalMinutes,
                                 final AmazonS3 s3,
                                 final String s3Bucket,
                                 final SSEAwsKeyManagementParams s3SSEKey,
                                 final AWSKMSClient awskmsClient,
                                 final String kmsUUIDKey,
                                 final SpeechResultDAODynamoDB speechResultDAODynamoDB) {
        this.clientConfiguration = clientConfiguration;
        this.consumerExecutor = consumerExecutor;
        this.scheduledIntervalMinutes = scheduledIntervalMinutes;
        this.s3 = s3;
        this.awskmsClient = awskmsClient;
        this.s3SSEKey = s3SSEKey;
        this.kmsUUIDKey = kmsUUIDKey;
        this.s3Bucket = s3Bucket;
        this.speechResultDAODynamoDB = speechResultDAODynamoDB;
    }

    @Override
    public void start() throws Exception {
        LOGGER.debug("action=start-kinesis-consumer running={}", isRunning);
        consumerExecutor.scheduleAtFixedRate(() -> {
            try {
                isRunning = true;
                consumeKinesisStream();
            } catch (Exception e) {
                isRunning = false;
                LOGGER.error("error=fail-to-start-kinesis-consumer error_msg={}", e.getMessage());
                try {
                    Thread.sleep(5000L);
                } catch (InterruptedException interrupted) {
                    LOGGER.warn("warning=kinesis-consumer-exit-interrupted-sleep");
                }
                System.exit(1);
            }}, scheduledIntervalMinutes, scheduledIntervalMinutes, TimeUnit.MINUTES);
    }

    @Override
    public void stop() throws Exception {
        LOGGER.debug("action=stop-kinesis-consumer");
        isRunning = false;
    }

    private void consumeKinesisStream() {
        final SpeechKinesisProcessorFactory factory = new SpeechKinesisProcessorFactory(s3Bucket, s3, s3SSEKey, awskmsClient, kmsUUIDKey, speechResultDAODynamoDB);
        LOGGER.info("info=kinesis-initial-position value={}", this.clientConfiguration.getInitialPositionInStream());
        final Worker worker = new Worker(factory, this.clientConfiguration);
        worker.run();
    }
}
