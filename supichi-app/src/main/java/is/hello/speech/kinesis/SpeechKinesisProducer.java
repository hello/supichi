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
                                 final BlockingQueue<KinesisData> inputQueue,
                                 final KinesisProducer kinesisProducer,
                                 final ExecutorService executor) {
        super(streamName, inputQueue, executor);
        this.streamName = streamName;
        this.kinesisProducer = kinesisProducer;
    }

    public Boolean addResult(final SpeechResult result, final byte[] audioBytes) {
        try {
            inputQueue.put(new KinesisData(result, audioBytes));
            return true;
        } catch (InterruptedException e) {
            LOGGER.warn("error=fail-to-put-audio-data-in-queue error_msg={}", e.getMessage());
            return false;
        }
    }

    @Override
    protected void putData() throws Exception {
        do {
            if (!inputQueue.isEmpty()) {
                LOGGER.debug("action=kpl-put-data queue_size={}", inputQueue.size());
                final SpeechResultsKinesis.SpeechResultsData speechResult = getSpeechResultsData(inputQueue.take());

                final String partitionKey = speechResult.getSenseId();
                final ByteBuffer payload = ByteBuffer.wrap(speechResult.toByteArray());

                while (kinesisProducer.getOutstandingRecordsCount() > KINESIS_MAX_RECORDS_IN_QUEUE) {
                    LOGGER.warn("warning=too-many-outstanding-records-in-kp records_size={} action=sleep-1", kinesisProducer.getOutstandingRecordsCount());
                    Thread.sleep(1);
                }

                kinesisProducer.addUserRecord(streamName, partitionKey, payload);
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
        try {
            LOGGER.debug("action=stop-kinesis-producer-sleep time=1-second why=wait-for-other-sleep-to-complete");
            Thread.sleep(2);
        } catch (InterruptedException e) {
            LOGGER.warn("warning=stop-sleep-interrupted!");
        }

        kinesisProducer.flushSync();
        kinesisProducer.destroy();
        super.stop();
    }

    private SpeechResultsKinesis.SpeechResultsData getSpeechResultsData(final KinesisData data) {
        LOGGER.debug("action=adding-to-kpl account_id={} sense_id={} uuid={} audio_size={}",
                data.speechResult.accountId, data.speechResult.senseId,
                data.speechResult.audioIdentifier, data.audioData.length);

        final SpeechResultsKinesis.SpeechResultsData.Builder builder = SpeechResultsKinesis.SpeechResultsData.newBuilder();
        if (data.audioData.length > 0) {
            // store audio path
            final SpeechResultsKinesis.AudioData audioData = SpeechResultsKinesis.AudioData.newBuilder()
                    .setDataSize(data.audioData.length)
                    .setData(ByteString.copyFrom(data.audioData))
                    .build();

            return builder.setAccountId(data.speechResult.accountId)
                    .setSenseId(data.speechResult.senseId)
                    .setCreated(DateTime.now(DateTimeZone.UTC).getMillis())
                    .setAudioUuid(data.speechResult.audioIdentifier)
                    .setAudio(audioData)
                    .build();
        }

        // TODO
        // final Set<Number> confidences = SpeechUtils.wakewordsMapToDDBAttribute(data.speechResult.wakeWordsConfidence);
        final Long currentTimestamp = DateTime.now(DateTimeZone.UTC).getMillis();

        return builder.setAccountId(data.speechResult.accountId)
                .setSenseId(data.speechResult.senseId)
                .setCreated(currentTimestamp)
                .setAudioUuid(data.speechResult.audioIdentifier)
                .setText(data.speechResult.text)
                .setService(data.speechResult.service.toString())
                .setConfidence(data.speechResult.confidence)
                .setIntent(data.speechResult.intent.toString())
                .setAction(data.speechResult.action.toString())
                .setIntentCategory(data.speechResult.intentCategory.toString())
                .setCommand(data.speechResult.command)
                .setWakeId(data.speechResult.wakeWord.getId())
//                .setWakeConfidence(confidences)
                .setResult(data.speechResult.result.toString())
                .setResponseText(data.speechResult.responseText)
                .setUpdated(currentTimestamp)
                .build();
    }
}
