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
import com.google.protobuf.InvalidProtocolBufferException;
import com.hello.suripu.core.speech.SpeechResultDAODynamoDB;
import is.hello.speech.core.api.SpeechResultsKinesis;
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
    private final SpeechResultDAODynamoDB speechResultDAODynamoDB;

    public SpeechKinesisRecordProcessor(final String s3Bucket, final AmazonS3 s3, final SpeechResultDAODynamoDB speechResultDAODynamoDB) {
        this.s3Bucket = s3Bucket;
        this.s3 = s3;
        this.speechResultDAODynamoDB = speechResultDAODynamoDB;
    }

    @Override
    public void initialize(String shardId) {}

    private boolean saveAudio(final SpeechResultsKinesis.SpeechResultsData speechResultsData) {
        LOGGER.debug("action=save-sense-upload-audio account_id={} sense_id={} uuid={} audio_size={}",
                speechResultsData.getAccountId(), speechResultsData.getSenseId(),
                speechResultsData.getAudioUuid(), speechResultsData.getAudio().getDataSize());

        final byte[] audioBytes = speechResultsData.getAudio().getData().toByteArray();
        final ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        metadata.setContentLength(audioBytes.length);

        final String keyname = String.format("%d-%s-%s.raw",
                speechResultsData.getAccountId(), speechResultsData.getSenseId(), speechResultsData.getAudioUuid());

        try {
            final PutObjectResult putResult = s3.putObject(
                    new PutObjectRequest(s3Bucket, keyname, new ByteArrayInputStream(audioBytes), metadata));

            LOGGER.debug("action=get-sense-audio-upload-result md5={}", putResult.getContentMd5());
        } catch (AmazonServiceException ase) {
            LOGGER.error("error=aws-exception status={} error_msg={}", ase.getStatusCode(), ase.getMessage());
            return false;
        } catch (AmazonClientException ace) {
            LOGGER.error("error=aws-client-exception error_msg={}", ace.getMessage());
            return false;
        }

        return true;
    }

    @Override
    public void processRecords(List<Record> records, IRecordProcessorCheckpointer checkpointer) {
        LOGGER.debug("info=number-of-records size={}", records.size());
        int numProcessed = 0;
        int numAudio = 0;
        for (final Record record : records) {
            final SpeechResultsKinesis.SpeechResultsData speechResultsData;
            try {
                speechResultsData = SpeechResultsKinesis.SpeechResultsData.parseFrom(record.getData().array());
                if (speechResultsData.hasAudio() && speechResultsData.getAudio().getDataSize() > 0) {
                    final boolean saved = saveAudio(speechResultsData);
                    if (saved) {
                        numAudio++;
                    }
                }
                numProcessed++;
            } catch (InvalidProtocolBufferException e) {
                LOGGER.error("error= fail-to-decode-speech-protobuf error_msg={}", e.getMessage());
            } catch (IllegalArgumentException e) {
                LOGGER.error("error=fail-to-decrypt-speech-result-data data={} error_msg={}", record.getData().array(), e.getMessage());
            }
        }

        LOGGER.debug("num_records_processed={} audio_saved={}", numProcessed, numAudio);

        if (numProcessed == records.size()) {
            try {
                checkpointer.checkpoint();
                LOGGER.debug("action=checkpoint-success");
            } catch (InvalidStateException e) {
                LOGGER.error("error=checkpoint-fail error_msg={}", e.getMessage());
            } catch (ShutdownException e) {
                LOGGER.error("error=received-shutdown-command-at-checkpoint action=bailing error_msg={}", e.getMessage());
            }

        }
    }

    @Override
    public void shutdown(IRecordProcessorCheckpointer checkpointer, ShutdownReason reason) {

        LOGGER.info("action=speech-kinesis-processor-shutting-down reason={}", reason);
        try {
            checkpointer.checkpoint();
        } catch (Exception e) {
            LOGGER.error("error=fail-to-checkpoint-before-shutting-down error_msg={}", e.getMessage());
        }
    }
}
