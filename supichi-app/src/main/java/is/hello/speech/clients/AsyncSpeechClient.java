package is.hello.speech.clients;

/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Client sends streaming audio to Speech.Recognize via gRPC and returns streaming transcription.
//
// Uses a service account for OAuth2 authentication, which you may obtain at
// https://console.developers.google.com
// API Manager > Google Cloud Speech API > Enable
// API Manager > Credentials > Create credentials > Service account key > New service account.
//
// Then set environment variable GOOGLE_APPLICATION_CREDENTIALS to the full path of that file.

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.AudioRequest;
import com.google.cloud.speech.v1.InitialRecognizeRequest;
import com.google.cloud.speech.v1.InitialRecognizeRequest.AudioEncoding;
import com.google.cloud.speech.v1.RecognizeRequest;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechGrpc;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.common.base.Optional;
import com.google.protobuf.ByteString;
import com.google.protobuf.TextFormat;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.auth.ClientAuthInterceptor;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import is.hello.speech.configuration.AudioConfiguration;
import is.hello.speech.core.models.SpeechResult;
import is.hello.speech.utils.HelloStreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.util.logging.resources.logging;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Client that sends streaming audio to Speech.Recognize and returns streaming transcript.
 */
public class AsyncSpeechClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncSpeechClient.class.getName());

    private final String host;
    private final int port;
    private final AudioConfiguration configuration;

    private final ManagedChannel channel;

    private final SpeechGrpc.SpeechStub stub;

    private static final List<String> OAUTH2_SCOPES =
            Arrays.asList("https://www.googleapis.com/auth/cloud-platform");

    /**
     * Construct client connecting to Cloud Speech server at {@code host:port}.
     */
    public AsyncSpeechClient(String host, int port, AudioConfiguration configuration) throws IOException {
        this.host = host;
        this.port = port;
        this.configuration = configuration;

        GoogleCredentials creds = GoogleCredentials.getApplicationDefault();
        creds = creds.createScoped(OAUTH2_SCOPES);
        channel = NettyChannelBuilder.forAddress(this.host, this.port)
                .negotiationType(NegotiationType.TLS)
                .intercept(new ClientAuthInterceptor(creds, Executors.newSingleThreadExecutor()))
                .build();
        stub = SpeechGrpc.newStub(channel);
        LOGGER.info("action=created-stub host={} port={}", this.host, this.port);
    }

    public void shutdown() throws InterruptedException {
//        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        channel.shutdownNow();
    }

    /** Send streaming recognize requests to server. */
    public Optional<String> recognize(byte[] body, int samplingRate) throws InterruptedException, IOException {
        final CountDownLatch finishLatch = new CountDownLatch(1);
        final String[] resp = new String[]{"failed"};

        StreamObserver<RecognizeResponse> responseObserver = new StreamObserver<RecognizeResponse>() {
            @Override
            public void onNext(RecognizeResponse response) {
                LOGGER.info("Received result: " +  TextFormat.printToString(response));
                for(final SpeechRecognitionResult result : response.getResultsList()) {
                    LOGGER.info("resp: {}", result);
                    if(result.getIsFinal()) {
                        LOGGER.info("Received result: " +  TextFormat.printToString(result));
                        resp[0] = result.getAlternatives(0).getTranscript();
                        LOGGER.info("resp: {}", resp[0]);
                        finishLatch.countDown();
                    }
                }
            }

            @Override
            public void onError(Throwable error) {
                Status status = Status.fromThrowable(error);
                LOGGER.warn("recognize failed: {}", status);
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                LOGGER.info("recognize completed.");
                finishLatch.countDown();
            }
        };

        final StreamObserver<RecognizeRequest> requestObserver = stub.recognize(responseObserver);
        try {
            // Build and send a RecognizeRequest containing the parameters for processing the audio.
            InitialRecognizeRequest initial = InitialRecognizeRequest.newBuilder()
                    .setEncoding(AudioEncoding.LINEAR16)
                    .setSampleRate(samplingRate)
                    .setInterimResults(true)
                    .build();
            RecognizeRequest firstRequest = RecognizeRequest.newBuilder()
                    .setInitialRequest(initial)
                    .build();
            requestObserver.onNext(firstRequest);

            // Open audio file. Read and send sequential buffers of audio as additional RecognizeRequests.
//            FileInputStream in = new FileInputStream(new File(file));
            final ByteArrayInputStream in = new ByteArrayInputStream(body);
            // For LINEAR16 at 16000 Hz sample rate, 3200 bytes corresponds to 100 milliseconds of audio.
            byte[] buffer = new byte[3200];
            int bytesRead;
            int totalBytes = 0;
            while ((bytesRead = in.read(buffer)) != -1) {
                totalBytes += bytesRead;
                final AudioRequest audio = AudioRequest.newBuilder()
                        .setContent(ByteString.copyFrom(buffer, 0, bytesRead))
                        .build();
                final RecognizeRequest request = RecognizeRequest.newBuilder()
                        .setAudioRequest(audio)
                        .build();
                requestObserver.onNext(request);
                // To simulate real-time audio, sleep after sending each audio buffer.
                // For 16000 Hz sample rate, sleep 100 milliseconds.
//                Thread.sleep(samplingRate / 160);
            }
            LOGGER.info("Sent " + totalBytes + " bytes from audio");
        } catch (RuntimeException e) {
            // Cancel RPC.
            requestObserver.onError(e);
            throw e;
        }
        // Mark the end of requests.
        requestObserver.onCompleted();

        // Receiving happens asynchronously.
        finishLatch.await(5, TimeUnit.SECONDS);
        return Optional.of(resp[0]);
    }


    /**
     *
     * @param in input stream buffer
     * @param samplingRate audio sampling rate
     * @return transcribed speech result
     * @throws InterruptedException
     * @throws IOException
     */
    public SpeechResult stream(final InputStream in, int samplingRate) throws InterruptedException, IOException {
        final CountDownLatch finishLatch = new CountDownLatch(1);


        final HelloStreamObserver responseObserver = new HelloStreamObserver(finishLatch);
        final StreamObserver<RecognizeRequest> requestObserver = stub.recognize(responseObserver);
        try {
            // Build and send a RecognizeRequest containing the parameters for processing the audio.
            final InitialRecognizeRequest initial = InitialRecognizeRequest.newBuilder()
                    .setEncoding(configuration.getEncoding()) // AudioEncoding.LINEAR16
                    .setSampleRate(samplingRate)
                    .setInterimResults(configuration.getInterimResultsPreference())
                    .build();

            final RecognizeRequest firstRequest = RecognizeRequest.newBuilder()
                    .setInitialRequest(initial)
                    .build();
            requestObserver.onNext(firstRequest);

            // Open audio file. Read and send sequential buffers of audio as additional RecognizeRequests.
            // FileInputStream in = new FileInputStream(new File(file));
            // For LINEAR16 at 16000 Hz sample rate, 3200 bytes corresponds to 100 milliseconds of audio.
            byte[] buffer = new byte[configuration.getBufferSize()];
            int bytesRead;
            int totalBytes = 0;
            while ((bytesRead = in.read(buffer)) != -1) {
                LOGGER.debug("action=read-bytes-from-input-stream bytes_read={}", bytesRead);

                totalBytes += bytesRead;
                final AudioRequest audio = AudioRequest.newBuilder()
                        .setContent(ByteString.copyFrom(buffer, 0, bytesRead))
                        .build();
                final RecognizeRequest request = RecognizeRequest.newBuilder()
                        .setAudioRequest(audio)
                        .build();
                requestObserver.onNext(request);
            }
            LOGGER.info("action=sent-bytes-from-audio total_bytes={}", totalBytes);
        } catch (RuntimeException e) {
            // Cancel RPC.
            LOGGER.error("error=stream-audio-fail");
            requestObserver.onError(e);
            throw e;
        }

        // Mark the end of requests.
        requestObserver.onCompleted();

        // Receiving happens asynchronously.
        finishLatch.await(10, TimeUnit.SECONDS);

        in.close(); // do we need to close this ourselves?
        return responseObserver.result();
    }
}

