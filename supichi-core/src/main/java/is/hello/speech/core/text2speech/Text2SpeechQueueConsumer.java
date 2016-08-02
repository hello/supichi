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
import com.ibm.watson.developer_cloud.text_to_speech.v1.util.WaveUtils;
import io.dropwizard.lifecycle.Managed;
import is.hello.speech.core.api.Text2SpeechQueue;
import is.hello.speech.core.configuration.SQSConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Created by ksg on 6/28/16
 */
public class Text2SpeechQueueConsumer implements Managed {
    private final Logger LOGGER = LoggerFactory.getLogger(Text2SpeechQueueConsumer.class);

    private static final int MAX_RECEIVED_MESSAGES = 10;
    private static final AudioFormat DEFAULT_AUDIO_FORMAT = AudioFormat.WAV;
    private static final int WATSON_SAMPLING_RATE = 22050;
    private static final float TARGET_SAMPLING_RATE = 16000.0f;

    private static final String COMPRESS_PARAMS = "-r 16k -e ima-adpcm `";

    private final AmazonS3 amazonS3;
    private final String s3Bucket;
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
        this.s3Bucket = s3Bucket;
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

                        // Save Raw data to tmp file, upload tmp file to S3
                        final String uuid = UUID.randomUUID().toString();
//                        final String rawFilename = String.format("/tmp/tmp_%s.wav", uuid);
//                        final File rawFile = new File(rawFilename);
//                        final FileOutputStream fileOutputStream = new FileOutputStream(rawFile);
//                        ByteStreams.copy(stream, fileOutputStream);
//                        fileOutputStream.close();
//
//                        final String s3RawBucket = String.format("%s/%s/%s", s3KeyRaw, synthesizeMessage.getIntent().toString(), synthesizeMessage.getCategory().toString());
//                        amazonS3.putObject(new PutObjectRequest(s3RawBucket, String.format("%s-raw.wav", keyname), rawFile));
                        final InputStream newStream = WaveUtils.reWriteWaveHeader(stream);

                        // Process Audio
                        final Optional<AudioInputStream> downSampledAudio = downSampleAudio(newStream, TARGET_SAMPLING_RATE);
                        if (downSampledAudio.isPresent()) {
                            // Save to File
//                            final String processedFilename = String.format("/tmp/tmp_down_%s.wav", uuid);
//                            final File processedFile = new File(processedFilename);
//                            final int bytesWritten = AudioSystem.write(downSampledAudio.get(), AudioFileFormat.Type.WAVE, processedFile);
//                            LOGGER.debug("action=write-downsampled-audio-to-file filename={} size={}", processedFilename, bytesWritten);

                            // get stream size
                            // final int audioSize = downSampledAudio.get().available();
                            // LOGGER.debug("audio_size={}", audioSize);

                            // convert input stream to bytes
                            final byte [] audioBytes = WaveUtils.toByteArray(downSampledAudio.get());


                            // write to S3
                            final String S3Keyname = String.format("%s-16k-java2.wav", keyname);
                            final String S3Bucket = String.format("%s/%s/%s", s3Key, synthesizeMessage.getIntent().toString(), synthesizeMessage.getCategory().toString());
                            LOGGER.debug("action=upload-downsampled-s3 bucket={} key={}", S3Bucket, S3Keyname);

                            final ObjectMetadata metadata = new ObjectMetadata();
                            metadata.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                            metadata.setContentLength(audioBytes.length);

                            final InputStream s3Stream = new ByteArrayInputStream(audioBytes);

                            final PutObjectResult putResult = amazonS3.putObject(new PutObjectRequest(S3Bucket, S3Keyname, s3Stream, metadata));
                            LOGGER.debug("action=upload-downsampled-to-S3 md5={}", putResult.getContentMd5());

//                            if (processedFile.delete()) {
//                                LOGGER.info("action=delete-file-success filename={}", processedFilename);
//                            } else {
//                                LOGGER.info("action=delete-file-failure filename={}", processedFilename);
//                            }

                        }

                        // Compress: convert to AD-PCM 16K 6-bit audio and save to a different s3 bucket
                        // "sox test.wav -r 16k -e ima-adpcm -b 4 -c 1 output3.wav";
//                        final String processedFilename = "/tmp/tmp_compressed.ima";
//                        final String processCommand = String.format("sox -r 22050 -v 1.5 %s -r 16k -b 4 -c 1 -e ima-adpcm %s", rawFilename, processedFilename);

                        /**
                        // Shell out command to downsample: 16k
                        final String processedFilename = "/tmp/tmp_processed.wav";
                        final String processCommand = String.format("sox -r %d %s -r 16k %s", WATSON_SAMPLING_RATE, rawFilename, processedFilename);

                        try {
                            LOGGER.debug("action=compressing-file command={}", processCommand);
                            final Process process = Runtime.getRuntime().exec(processCommand);
                            final boolean returnCode = process.waitFor(5000L, TimeUnit.MILLISECONDS);
                            LOGGER.debug("action=compression-success return_code={}", returnCode);

                            // save converted file to S3
                            final File compressedFile = new File(processedFilename);
                            final String S3Keyname = String.format("%s-16k.wav", keyname);
                            final String S3Bucket = String.format("%s/%s/%s", s3Key, synthesizeMessage.getIntent().toString(), synthesizeMessage.getCategory().toString());
                            LOGGER.debug("action=upload-s3 bucket={} key={}", S3Bucket, S3Keyname);
                            amazonS3.putObject(new PutObjectRequest(S3Bucket, S3Keyname, compressedFile));

                        } catch (IOException | InterruptedException e) {
                            LOGGER.error("action=fail-to-sox key={} error={}", keyname, e.getMessage());
                        }
                         **/

                    }

                    processedHandlers.add(new DeleteMessageBatchRequestEntry(message.messageId, message.messageHandler));
                }

                if (!processedHandlers.isEmpty()) {
                    LOGGER.debug("action=clearing-processed-messages-from-queue size={}", processedHandlers.size());
                    final DeleteMessageBatchResult deleteResult = sqsClient.deleteMessageBatch(
                            new DeleteMessageBatchRequest(sqsQueueUrl, processedHandlers));
                    LOGGER.debug("action=delete-messages size={}", deleteResult.getSuccessful().size());
                }
            }

        } while (isRunning);

        LOGGER.info("key=text2speech-queue-consumer action=consume-text2speech-queue-done");
    }

    private Optional<AudioInputStream> downSampleAudio(final InputStream inputStream, final float targetSampleRate) {

        AudioInputStream sourceStream;
        try {
            // final InputStream bufferedStream = new BufferedInputStream(inputStream);
            sourceStream = AudioSystem.getAudioInputStream(inputStream);
        } catch (UnsupportedAudioFileException e) {
            LOGGER.error("error=unsupported-audio-file msg={}", e.getMessage());
            return Optional.absent();
        } catch (IOException e) {
            LOGGER.error("error=io-exception msg={}", e.getMessage());
            return Optional.absent();
        }

        final javax.sound.sampled.AudioFormat sourceFormat = sourceStream.getFormat();
        final javax.sound.sampled.AudioFormat targetFormat = new javax.sound.sampled.AudioFormat(
                sourceFormat.getEncoding(),
                targetSampleRate,
                sourceFormat.getSampleSizeInBits(),
                sourceFormat.getChannels(),
                sourceFormat.getFrameSize(),
                targetSampleRate,
                sourceFormat.isBigEndian()
        );

        final AudioInputStream convertedStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream);
        return Optional.of(convertedStream);
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
