package is.hello.speech.kinesis;


import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.InvalidStateException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.ShutdownException;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorCheckpointer;
import com.amazonaws.services.kinesis.clientlibrary.types.ShutdownReason;
import com.amazonaws.services.kinesis.model.Record;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.SSEAwsKeyManagementParams;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hello.suripu.core.speech.SpeechResultDAODynamoDB;
import com.hello.suripu.core.speech.SpeechTimeline;
import com.hello.suripu.core.speech.SpeechTimelineIngestDAO;
import is.hello.speech.core.api.SpeechResultsKinesis;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * Created by ksg on 8/11/16
 */
public class SpeechKinesisRecordProcessor implements IRecordProcessor {
    private final Logger LOGGER = LoggerFactory.getLogger(SpeechKinesisRecordProcessor.class);

    private final AmazonS3 s3;
    private final String s3Bucket;
    private final SSEAwsKeyManagementParams s3SSEKey;
    private final SpeechTimelineIngestDAO speechTimelineIngestDAO;
    private final SpeechResultDAODynamoDB speechResultDAODynamoDB;

    public SpeechKinesisRecordProcessor(final String s3Bucket,
                                        final AmazonS3 s3,
                                        final SSEAwsKeyManagementParams s3SSEKey,
                                        final SpeechTimelineIngestDAO speechTimelineIngestDAO,
                                        final SpeechResultDAODynamoDB speechResultDAODynamoDB) {
        this.s3Bucket = s3Bucket;
        this.s3 = s3;
        this.s3SSEKey = s3SSEKey;
        this.speechTimelineIngestDAO = speechTimelineIngestDAO;
        this.speechResultDAODynamoDB = speechResultDAODynamoDB;
    }

    @Override
    public void initialize(String shardId) {}

    private void saveTimeline(final SpeechResultsKinesis.SpeechResultsData speechResultsData, final String sequenceNumber) {
        // save timeline
        final SpeechTimeline speechTimeline = new SpeechTimeline(
                speechResultsData.getAccountId(),
                new DateTime(speechResultsData.getCreated(), DateTimeZone.UTC),
                speechResultsData.getSenseId(),
                speechResultsData.getAudioUuid());

        try {
            final boolean savedTimeline = speechTimelineIngestDAO.putItem(speechTimeline);
            if (!savedTimeline) {
                LOGGER.error("error=fail-to-save-speech-timeline account_id={} sense_id={} sequence_number={}",
                        speechResultsData.getAccountId(), speechResultsData.getSenseId(), sequenceNumber);
            }
        } catch (AmazonServiceException ase) {
            LOGGER.error("error=aws-service-exception status={} error_msg={} action=save-speech-timeline-exiting sequence_number={}",
                    ase.getStatusCode(), ase.getMessage(), sequenceNumber);
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException ignored) {
            }
            System.exit(1);
        } catch (AmazonClientException ace) {
            LOGGER.error("error=aws-client-exception error_msg={} action=speech-timeline sequence_number={}", ace.getMessage(), sequenceNumber);
        }
    }

    private boolean saveAudio(final SpeechResultsKinesis.SpeechResultsData speechResultsData, final String sequenceNumber) {
        final Long accountId = speechResultsData.getAccountId();
        final String senseId = speechResultsData.getSenseId();
        LOGGER.debug("action=save-sense-upload-audio account_id={} sense_id={} uuid={} audio_size={}",
                accountId, senseId,
                speechResultsData.getAudioUuid(), speechResultsData.getAudio().getDataSize());

        final byte[] audioBytes = speechResultsData.getAudio().getData().toByteArray();
        final ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        metadata.setContentLength(audioBytes.length);

        final String keyname = String.format("%s.raw", speechResultsData.getAudioUuid());
        try {
            final PutObjectResult putResult = s3.putObject(
                    new PutObjectRequest(s3Bucket, keyname, new ByteArrayInputStream(audioBytes), metadata)
                            .withSSEAwsKeyManagementParams(s3SSEKey)
            );
            LOGGER.debug("action=get-sense-audio-upload-result md5={}", putResult.getContentMd5());

        } catch (AmazonServiceException ase) {
            LOGGER.error("error=aws-exception status={} error_msg={} action=s3 sequence_number={}",
                    ase.getStatusCode(), ase.getMessage(), sequenceNumber);
        } catch (AmazonClientException ace) {
            LOGGER.error("error=aws-client-exception error_msg={} action=s3 sequence_number={}",
                    ace.getMessage(), sequenceNumber);
        }

        return true;
    }

    @Override
    public void processRecords(List<Record> records, IRecordProcessorCheckpointer checkpointer) {
        LOGGER.debug("info=number-of-records size={}", records.size());
        int numAudio = 0;
        for (final Record record : records) {
            final SpeechResultsKinesis.SpeechResultsData speechResultsData;
            try {
                speechResultsData = SpeechResultsKinesis.SpeechResultsData.parseFrom(record.getData().array());
                if (speechResultsData.hasAudio() && speechResultsData.getAudio().getDataSize() > 0) {

                    saveTimeline(speechResultsData, record.getSequenceNumber());

                    final boolean saved = saveAudio(speechResultsData, record.getSequenceNumber());
                    if (saved) {
                        numAudio++;
                    }
                }
            } catch (InvalidProtocolBufferException e) {
                LOGGER.error("error= fail-to-decode-speech-protobuf error_msg={} sequence_number={} partition_key={}",
                        e.getMessage(), record.getSequenceNumber(), record.getPartitionKey());
            } catch (IllegalArgumentException e) {
                LOGGER.error("error=fail-to-decrypt-speech-result-data data={} error_msg={} sequence_number={} partition_key={}",
                        record.getData().array(), e.getMessage(), record.getSequenceNumber(), record.getPartitionKey());
            }
        }

        LOGGER.debug("audio_saved={}", numAudio);

        try {
            checkpointer.checkpoint();
            LOGGER.debug("action=checkpoint-success");
        } catch (InvalidStateException e) {
            LOGGER.error("error=checkpoint-fail error_msg={}", e.getMessage());
        } catch (ShutdownException e) {
            LOGGER.error("error=received-shutdown-command-at-checkpoint action=bailing error_msg={}", e.getMessage());
        }

    }

    @Override
    public void shutdown(IRecordProcessorCheckpointer checkpointer, ShutdownReason reason) {
        LOGGER.info("action=speech-kinesis-processor-shutting-down reason={}", reason);
        if (reason.equals(ShutdownReason.ZOMBIE)) {
            try {
                checkpointer.checkpoint();
            } catch (Exception e) {
                LOGGER.error("error=fail-to-checkpoint-before-shutting-down error_msg={}", e.getMessage());
            }
        }
    }
}
