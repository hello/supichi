package is.hello.speech.core.text2speech;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
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
import is.hello.speech.core.api.Text2SpeechQueue;
import is.hello.speech.core.configuration.SQSConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by ksg on 6/28/16
 */
public class Text2SpeechQueueConsumer implements Managed {
    private final Logger LOGGER = LoggerFactory.getLogger(Text2SpeechQueueConsumer.class);

    private static final int MAX_RECEIVED_MESSAGES = 10;
    private static final AudioFormat DEFAULT_AUDIO_FORMAT = AudioFormat.WAV;

    private static final String COMPRESS_PARAMS = "-r 16k -e ima-adpcm `";

    private final AmazonS3 amazonS3;
    private final String s3Bucket;
    private final String s3KeyRaw;
    private final String s3KeyCompressed;

    private final TextToSpeech watson;
    private final Voice watsonVoice;

    private final AmazonSQSAsync sqsClient;
    private final String sqsQueueUrl;
    private final SQSConfiguration sqsConfiguration;

    private final ExecutorService consumerExecutor;

    private boolean isRunning = false;

    public Text2SpeechQueueConsumer(final AmazonS3 amazonS3, final String s3Bucket,
                                    final String s3PrefixRaw, final String s3PrefixCompressed,
                                    final TextToSpeech watson, final String voice,
                                    final AmazonSQSAsync sqsClient, final String sqsQueueUrl, final SQSConfiguration sqsConfiguration,
                                    final ExecutorService consumerExecutor) {
        this.amazonS3 = amazonS3;
        this.s3Bucket = s3Bucket;
        this.s3KeyRaw = String.format("%s/%s", s3Bucket, s3PrefixRaw);
        this.s3KeyCompressed = String.format("%s/%s", s3Bucket, s3PrefixCompressed);
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

                        final InputStream stream = watson.synthesize(text, watsonVoice, DEFAULT_AUDIO_FORMAT).execute();

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

                        // save to file
                        final String rawFilename = "/tmp/tmp.wav";
                        final File rawFile = new File(rawFilename);
                        final FileOutputStream fileOutputStream = new FileOutputStream(rawFile);
                        ByteStreams.copy(stream, fileOutputStream);
                        fileOutputStream.close();

                        // save to S3

//                        // save directly from stream
//                         final ObjectMetadata metadata = new ObjectMetadata();
//                         metadata.setContentType(MediaType.APPLICATION_OCTET_STREAM);
//
//                        // if we want to set content-length
//                        // byte[] bytes = IOUtils.toByteArray(stream);
//                        // final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
//                        // metadata.setContentLength(bytes.length);
//
//                         amazonS3.putObject(new PutObjectRequest(this.s3KeyRaw, filename, stream, metadata));


                        // Save Raw data from tmp file
                        final String s3RawBucket = String.format("%s/%s/%s", s3KeyRaw, synthesizeMessage.getIntent().toString(), synthesizeMessage.getCategory().toString());
                        amazonS3.putObject(new PutObjectRequest(s3RawBucket, String.format("%s-raw.wav", keyname), rawFile));


                        // convert to AD-PCM 16K 6-bit audio and save to a different s3 bucket
                        // "sox test.wav -r 16k -e ima-adpcm -b 4 -c 1 output3.wav";
                        final String compressedFilename = "/tmp/tmp_compressed.ima";
                        final String compressCommand = String.format("sox -r 22050 -v 1.5 %s -r 16k -b 4 -c 1 -e ima-adpcm %s", rawFilename, compressedFilename);
                        try {
                            LOGGER.debug("action=compressing-file command={}", compressCommand);
                            final Process process2 = Runtime.getRuntime().exec(compressCommand);
                            final boolean returnCode2 = process2.waitFor(5000L, TimeUnit.MILLISECONDS);
                            LOGGER.debug("action=compression-success return_code={}", returnCode2);

                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        final File compressedFile = new File(compressedFilename);
                        final String S3Keyname = String.format("%s-compressed.ima", keyname);
                        final String S3Bucket = String.format("%s/%s/%s", s3KeyCompressed, synthesizeMessage.getIntent().toString(), synthesizeMessage.getCategory().toString());
                        LOGGER.debug("action=upload-s3 bucket={} key={}", S3Bucket, S3Keyname);
                        amazonS3.putObject(new PutObjectRequest(S3Bucket, S3Keyname, compressedFile));

                    }

                    processedHandlers.add(new DeleteMessageBatchRequestEntry(message.messageId, message.messageHandler));
                }

                if (!processedHandlers.isEmpty()) {
                    LOGGER.debug("action=clearing-processed-messages-from-queue size={}", processedHandlers.size());
                    final DeleteMessageBatchResult deleteResult = sqsClient.deleteMessageBatch(
                            new DeleteMessageBatchRequest(sqsQueueUrl, processedHandlers));
                    LOGGER.debug("action=delete-messages size={}", deleteResult.getSuccessful().size());
                }
            } else {
                // no messages, sleep for a bit TODO use a scheduled thread?
                // Thread.sleep(1000L);
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
                decodedMessages.add(new Text2SpeechMessage(message.getReceiptHandle(), message.getMessageId(), Optional.absent()));
            }
        }

        return decodedMessages;
    }
}
