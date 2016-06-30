package is.hello.speech.workers.text2speech;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.DeleteMessageBatchResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.AudioFormat;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.Voice;
import io.dropwizard.lifecycle.Managed;
import is.hello.speech.workers.framework.SQSConfiguration;
import is.hello.speech.workers.framework.SaveAudioConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
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

    private final AmazonS3 amazonS3;
    private final SaveAudioConfiguration s3Config;

    private final TextToSpeech watson;
    private final Voice watsonVoice;

    private final AmazonSQSAsync sqsClient;
    private final String sqsQueueUrl;
    private final SQSConfiguration sqsConfiguration;

    private final ExecutorService consumerExecutor;

    private boolean isRunning = false;

    public Text2SpeechQueueConsumer(final AmazonS3 amazonS3, final SaveAudioConfiguration s3Config,
                                    final TextToSpeech watson, final String voice,
                                    final AmazonSQSAsync sqsClient, final String sqsQueueUrl, final SQSConfiguration sqsConfiguration,
                                    final ExecutorService consumerExecutor) {
        this.amazonS3 = amazonS3;
        this.s3Config = s3Config;
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

            for (final Text2SpeechMessage message : messages) {
                if (message.synthesizeMessage.isPresent()) {
                    final Text2SpeechQueue.SynthesizeMessage synthesizeMessage = message.synthesizeMessage.get();
                    final String text = synthesizeMessage.getText();

                    // text-to-speech conversion
                    LOGGER.debug("action=synthesize-text service={} text={}", synthesizeMessage.getService().toString(), text);

                    final InputStream stream = watson.synthesize(text, watsonVoice, DEFAULT_AUDIO_FORMAT).execute();

                    // construct filename
                    final String parameterString = "whatever";
                    String filename = String.format("/Users/kingshy/DEV/Hello/supichi/WATSON_RESULTS/%s_%s_%s_%s_%s_%s.wav",
                            synthesizeMessage.getIntent().toString(),
                            synthesizeMessage.getAction().toString(),
                            synthesizeMessage.getCategory().toString(),
                            parameterString,
                            synthesizeMessage.getService().toString(),
                            synthesizeMessage.getVoice().toString()
                    );

                    LOGGER.debug("action=save-audio-to-file filename={}", filename);

                    // save to file
                    final File file = new File(filename);
                    ByteStreams.copy(stream, new FileOutputStream(file));

                    // TODO: save to S3
                }

                processedHandlers.add(new DeleteMessageBatchRequestEntry(message.messageId, message.messageHandler));
            }

            if (!processedHandlers.isEmpty()) {
                LOGGER.debug("action=clearing-processed-messages-from-queue size={}", processedHandlers.size());
                final DeleteMessageBatchResult deleteResult = sqsClient.deleteMessageBatch(
                        new DeleteMessageBatchRequest(sqsQueueUrl, processedHandlers));
                LOGGER.debug("action=delete-messages size={}", deleteResult.getSuccessful().size());
            }

        } while (isRunning);

        LOGGER.info("key=text2speech-queue-consumer action=consume-text2speech-queue-done");
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
                decodedMessages.add(new Text2SpeechMessage(message.getReceiptHandle(), message.getMessageId(), Optional.absent());
            }
        }

        return decodedMessages;
    }
}
