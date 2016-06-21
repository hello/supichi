package is.hello.speech.utils;

import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.common.base.Optional;
import com.google.protobuf.TextFormat;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import is.hello.speech.clients.AsyncSpeechClient;
import is.hello.speech.core.models.ResultGetter;
import is.hello.speech.core.models.SpeechResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public class HelloStreamObserver implements StreamObserver<RecognizeResponse>, ResultGetter {

    private static final Logger logger =
            LoggerFactory.getLogger(AsyncSpeechClient.class.getName());

    private SpeechResult speechResult = new SpeechResult();


    private final CountDownLatch finishLatch;

    public HelloStreamObserver(final CountDownLatch latch) {
        this.finishLatch = latch;
    }

    @Override
    public void onNext(final RecognizeResponse response) {
        for(final SpeechRecognitionResult result : response.getResultsList()) {
            logger.info("action=received-api-result result={}", TextFormat.printToString(result));

            speechResult.setStability(result.getStability());
            speechResult.setConfidence(result.getAlternatives(0).getConfidence());
            speechResult.setTranscript(Optional.of(result.getAlternatives(0).getTranscript()));
            logger.debug("action=get-interim-api-result result={}", speechResult);

            if(result.getIsFinal()) {
                speechResult.setFinal(true);
                logger.info("action=get-final-result result={}", speechResult);
                finishLatch.countDown();
            }

        }
    }

    @Override
    public void onError(Throwable throwable) {
        Status status = Status.fromThrowable(throwable);
        logger.warn("warning=stream-recognize-failed status={}", status);
        finishLatch.countDown();
    }

    @Override
    public void onCompleted() {
        logger.info("action=stream-recognize-completed");
        finishLatch.countDown();
    }

    @Override
    public SpeechResult result() {
        return speechResult;
    }
}
