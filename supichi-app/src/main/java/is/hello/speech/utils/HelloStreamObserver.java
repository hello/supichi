package is.hello.speech.utils;

import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.common.base.Optional;
import com.google.protobuf.TextFormat;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import is.hello.speech.clients.AsyncSpeechClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public class HelloStreamObserver implements StreamObserver<RecognizeResponse>, ResultGetter {

    private static final Logger logger =
            LoggerFactory.getLogger(AsyncSpeechClient.class.getName());

    private volatile Optional<String> defaultValue = Optional.absent();

    private final CountDownLatch finishLatch;

    public HelloStreamObserver(final CountDownLatch latch) {
        this.finishLatch = latch;
    }

    @Override
    public void onNext(final RecognizeResponse response) {
        for(final SpeechRecognitionResult result : response.getResultsList()) {
            logger.info("Received result: " +  TextFormat.printToString(result));
            if(result.getIsFinal()) {
                logger.info("Received result: " +  TextFormat.printToString(result));
                defaultValue = Optional.of(result.getAlternatives(0).getTranscript());
                logger.info("resp: {}", defaultValue);
                finishLatch.countDown();
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        Status status = Status.fromThrowable(throwable);
        logger.warn("stream recognize failed: {}", status);
        finishLatch.countDown();
    }

    @Override
    public void onCompleted() {
        logger.info("stream recognize completed.");
        finishLatch.countDown();
    }

    @Override
    public Optional<String> result() {
        return defaultValue;
    }
}
