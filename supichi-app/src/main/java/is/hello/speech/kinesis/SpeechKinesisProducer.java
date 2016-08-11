package is.hello.speech.kinesis;

import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.google.protobuf.ByteString;
import com.hello.suripu.core.speech.SpeechResult;
import is.hello.speech.core.api.SpeechResultsKinesis;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

/**
 * Created by ksg on 8/9/16
 */
public class SpeechKinesisProducer extends AbstractSpeechKinesisProducer {

    private final static Logger LOGGER = LoggerFactory.getLogger(SpeechKinesisProducer.class);

    private final static Long KINESIS_MAX_RECORDS_IN_QUEUE = 10000L;

    private final String streamName;
    private final KinesisProducer kinesisProducer;


    public SpeechKinesisProducer(final String streamName,
                                 final BlockingQueue<SpeechResultsKinesis.SpeechResultsData> inputQueue,
                                 final KinesisProducer kinesisProducer,
                                 final ExecutorService executor) {
        super(streamName, inputQueue, executor);
        this.streamName = streamName;
        this.kinesisProducer = kinesisProducer;
    }

    public Boolean addResult(final SpeechResult result, final byte [] audioBytes) {
        final SpeechResultsKinesis.SpeechResultsData.Builder builder = SpeechResultsKinesis.SpeechResultsData.newBuilder();
        if (audioBytes.length > 0) {
            // store audio path
            final SpeechResultsKinesis.AudioData audioData = SpeechResultsKinesis.AudioData.newBuilder()
                    .setDataSize(audioBytes.length)
                    .setData(ByteString.copyFrom(audioBytes))
                    .build();

            final SpeechResultsKinesis.SpeechResultsData speechResult = builder.setAccountId(result.accountId)
                    .setSenseId(result.senseId)
                    .setCreated(DateTime.now(DateTimeZone.UTC).getMillis())
                    .setAudioUuid(result.audioIdentifier)
                    .setAudio(audioData)
                    .build();
            try {
                inputQueue.put(speechResult);
                return true;
            } catch (InterruptedException e) {
                LOGGER.warn("error=fail-to-put-audio-data-in-queue error_msg={}", e.getMessage());
                return false;
            }
        }

        //        final Set<Number> confidences = SpeechUtils.wakewordsMapToDDBAttribute(result.wakeWordsConfidence);

        final Long currentTimestamp = DateTime.now(DateTimeZone.UTC).getMillis();
        final SpeechResultsKinesis.SpeechResultsData speechResult = builder.setAccountId(result.accountId)
                .setSenseId(result.senseId)
                .setCreated(currentTimestamp)
                .setAudioUuid(result.audioIdentifier)
                .setText(result.text)
                .setService(result.service.toString())
                .setConfidence(result.confidence)
                .setIntent(result.intent.toString())
                .setAction(result.action.toString())
                .setIntentCategory(result.intentCategory.toString())
                .setCommand(result.command)
                .setWakeId(result.wakeWord.getId())
//                .setWakeConfidence(confidences)
                .setResult(result.result.toString())
                .setResponseText(result.responseText)
                .setUpdated(currentTimestamp)
                .build();

        try {
            inputQueue.put(speechResult);
            return true;
        } catch (InterruptedException e) {
            LOGGER.warn("error=fail-to-put-speech-result-in-queue error_msg={}", e.getMessage());
            return false;
        }
    }


    @Override
    protected void putData() throws Exception {
        do {
            if (!inputQueue.isEmpty()) {
                LOGGER.debug("action=kpl-put-data queue_size={}", inputQueue.size());
                final SpeechResultsKinesis.SpeechResultsData speechResult = inputQueue.take();
                final String partitionKey = speechResult.getSenseId();
                final ByteBuffer kinesisData = ByteBuffer.wrap(speechResult.toByteArray());
                while (kinesisProducer.getOutstandingRecordsCount() > KINESIS_MAX_RECORDS_IN_QUEUE) {
                    LOGGER.warn("warning=too-many-outstanding-records-in-kp records_size={} action=sleep-1", kinesisProducer.getOutstandingRecordsCount());
                    Thread.sleep(1);
                }
                kinesisProducer.addUserRecord(streamName, partitionKey, kinesisData);
                recordsPut.getAndIncrement();
            }
        } while (isRunning);
    }

    @Override
    public long recordsPut() {
        return super.recordsPut() - kinesisProducer.getOutstandingRecordsCount();
    }

    @Override
    public void stop() {
        kinesisProducer.flushSync();
        kinesisProducer.destroy();
        super.stop();
    }
}
