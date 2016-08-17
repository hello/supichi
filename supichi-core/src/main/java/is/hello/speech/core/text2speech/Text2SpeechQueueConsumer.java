package is.hello.speech.core.text2speech;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.DeleteMessageBatchResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.AudioFormat;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.Voice;
import io.dropwizard.lifecycle.Managed;
import is.hello.speech.core.api.Text2SpeechQueue;
import is.hello.speech.core.configuration.SQSConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Created by ksg on 6/28/16
 */
public class Text2SpeechQueueConsumer implements Managed {
    private final Logger LOGGER = LoggerFactory.getLogger(Text2SpeechQueueConsumer.class);

    private static final int MAX_RECEIVED_MESSAGES = 10;
    private static final AudioFormat DEFAULT_AUDIO_FORMAT = AudioFormat.WAV;
    private static final float TARGET_SAMPLING_RATE = 16000.0f;

    private final AmazonS3 amazonS3;
    private final String s3KeyRaw;
    private final String s3Key;

    private final TextToSpeech watson;
    private final Voice watsonVoice;

    private final AmazonSQSAsync sqsClient;
    private final String sqsQueueUrl;
    private final SQSConfiguration sqsConfiguration;

    private final ExecutorService consumerExecutor;

    private boolean isRunning = false;

    public Text2SpeechQueueConsumer(final AmazonS3 amazonS3, final String s3Bucket,
                                    final String s3PrefixRaw, final String s3Prefix,
                                    final TextToSpeech watson, final String voice,
                                    final AmazonSQSAsync sqsClient, final String sqsQueueUrl, final SQSConfiguration sqsConfiguration,
                                    final ExecutorService consumerExecutor) {
        this.amazonS3 = amazonS3;
        this.s3KeyRaw = String.format("%s/%s", s3Bucket, s3PrefixRaw);
        this.s3Key = String.format("%s/%s", s3Bucket, s3Prefix);
        this.watson = watson;
        this.watsonVoice = this.watson.getVoice(voice).execute();
        this.sqsClient = sqsClient;
        this.sqsQueueUrl = sqsQueueUrl;
        this.sqsConfiguration = sqsConfiguration;
        this.consumerExecutor = consumerExecutor;
    }

    @Override
    public void start() throws Exception {
        LOGGER.info("key=text2speech-queue-consumer action=start-consuming-queue");
        consumerExecutor.execute(() -> {
            try {
                isRunning = true;
                consumeQueue();
            } catch (Exception exception) {
                isRunning = false;
                LOGGER.error("key=text2speech-queue-consumer error=fail-to-start-thread exception_msg={}", exception.getMessage());
                LOGGER.error("key=text2speech-queue-consumer action=forcing-exit");
                try {
                    Thread.sleep(5000L);
                } catch (InterruptedException interrupted) {
                    LOGGER.warn("key=text2speech-queue-consumer action=interrupted-sleep");
                }
                System.exit(1);
            }
        });
    }

    @Override
    public void stop() throws Exception {
        LOGGER.info("key=text2speech-queue-consumer action=queue-consumer-stopped");
        isRunning = false;

    }

    private void consumeQueue() throws Exception {
        do {
            // get a batch of messages
            final List<Text2SpeechMessage> messages = receiveMessages();
            final List<DeleteMessageBatchRequestEntry> processedHandlers = Lists.newArrayList();

            if (!messages.isEmpty()) {
                for (final Text2SpeechMessage message : messages) {
                    if (message.synthesizeMessage.isPresent()) {
                        final Text2SpeechQueue.SynthesizeMessage synthesizeMessage = message.synthesizeMessage.get();
                        final String text = synthesizeMessage.getText();

                        // text-to-speech conversion
                        LOGGER.debug("action=synthesize-text service={} text={}", synthesizeMessage.getService().toString(), text);

                        try {
                            final InputStream watsonStream = watson.synthesize(text, watsonVoice, DEFAULT_AUDIO_FORMAT).execute();

                            // construct filename
                            String keyname = String.format("%s-%s-%s-%s-%s-%s",
                                    synthesizeMessage.getIntent().toString(),
                                    synthesizeMessage.getAction().toString(),
                                    synthesizeMessage.getCategory().toString(),
                                    synthesizeMessage.getParametersString(),
                                    synthesizeMessage.getService().toString(),
                                    synthesizeMessage.getVoice().toString()
                            );

                            LOGGER.debug("action=save-audio-to-file filename={}", keyname);

                            // re-write correct header for Watson audio stream (needed for down-sampling)
                            final AudioUtils.AudioBytes watsonAudio = AudioUtils.convertStreamToBytesWithWavHeader(watsonStream);
                            if (watsonAudio.contentSize != 0) {

                                // upload raw data to S3
                                final String s3RawBucket = String.format("%s/%s/%s", s3KeyRaw, synthesizeMessage.getIntent().toString(), synthesizeMessage.getCategory().toString());
                                uploadToS3(s3RawBucket, String.format("%s-raw.wav", keyname), watsonAudio.bytes);

                                // equalized audio first
                                final AudioUtils.AudioBytes equalizedBytes = AudioUtils.equalize(watsonAudio.bytes, Optional.absent());

                                // down-sample audio from 22050 to 16k, upload converted bytes to S3
                                final AudioUtils.AudioBytes downSampledBytes = AudioUtils.downSampleAudio(equalizedBytes.bytes, equalizedBytes.format, TARGET_SAMPLING_RATE);
                                if (downSampledBytes.contentSize != 0) {
                                    final String S3Bucket = String.format("%s/%s/%s", s3Key, synthesizeMessage.getIntent().toString(), synthesizeMessage.getCategory().toString());
                                    uploadToS3(S3Bucket, String.format("%s-16k.wav", keyname), downSampledBytes.bytes);

                                    // convert to mp3
                                    final byte[] mp3Bytes = AudioUtils.encodePcmToMp3(downSampledBytes);
                                    if (mp3Bytes.length > 0) {
                                        uploadToS3(S3Bucket, String.format("%s-16k.mp3", keyname), mp3Bytes);
                                    }
                                }
                            }

                            watsonStream.close();

                        } catch (Exception e) {
                            LOGGER.error("error=watson-audio-fail error_msg={}", e.getMessage());
                        }

                    }

                    processedHandlers.add(new DeleteMessageBatchRequestEntry(message.messageId, message.messageHandler));
                }

                if (!processedHandlers.isEmpty()) {
                    // delete processed SQS message
                    LOGGER.debug("action=clearing-processed-messages-from-queue size={}", processedHandlers.size());
                    final DeleteMessageBatchResult deleteResult = sqsClient.deleteMessageBatch(
                            new DeleteMessageBatchRequest(sqsQueueUrl, processedHandlers));
                    LOGGER.debug("action=delete-messages size={}", deleteResult.getSuccessful().size());
                }
            }

        } while (isRunning);

        LOGGER.info("key=text2speech-queue-consumer action=consume-text2speech-queue-done");
    }

    private void uploadToS3(String bucketName, String keyname, byte[] bytes) {
        LOGGER.debug("action=upload-s3 bucket={} key={} content_length={}", bucketName, keyname, bytes.length);

        final ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        metadata.setContentLength(bytes.length);

        final PutObjectResult putResult = amazonS3.putObject(new PutObjectRequest(bucketName, keyname, new ByteArrayInputStream(bytes), metadata));

        LOGGER.debug("action=upload-result md5={}", putResult.getContentMd5());
    }



    private List<Text2SpeechMessage> receiveMessages() {
        final ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest()
                .withQueueUrl(this.sqsQueueUrl)
                .withMaxNumberOfMessages(MAX_RECEIVED_MESSAGES)
                .withVisibilityTimeout(this.sqsConfiguration.getSqsVisibilityTimeoutSeconds())
                .withWaitTimeSeconds(this.sqsConfiguration.getSqsWaitTimeSeconds());

        final ReceiveMessageResult rx = sqsClient.receiveMessage(receiveMessageRequest);
        final List<Message> messages = rx.getMessages();

        final List<Text2SpeechMessage> decodedMessages = Lists.newArrayListWithExpectedSize(messages.size());

        for (final Message message : messages) {

            try {
                final Text2SpeechQueue.SynthesizeMessage synthesizeMessage = Text2SpeechUtils.decodeMessage(message.getBody());
                decodedMessages.add(new Text2SpeechMessage(message.getReceiptHandle(), message.getMessageId(), Optional.of(synthesizeMessage)));
            } catch (IOException e) {
                LOGGER.error("action=decode-sqs-message-fail message_id={}", message.getMessageId());
                decodedMessages.add(new Text2SpeechMessage(message.getReceiptHandle(), message.getMessageId(), Optional.absent()));
            }
        }

        return decodedMessages;
    }

}
