package is.hello.speech.kinesis;


import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ksg on 8/9/16
 */
public abstract class AbstractSpeechKinesisProducer implements Managed {

    private final static Logger LOGGER = LoggerFactory.getLogger(AbstractSpeechKinesisProducer.class);

    private final String streamName;
    protected final BlockingQueue<KinesisData> inputQueue;

    protected final ExecutorService executor;

    protected volatile boolean isRunning = false;
    protected final AtomicLong recordsPut = new AtomicLong(0);

    protected AbstractSpeechKinesisProducer(final String streamName,
                                            final BlockingQueue<KinesisData> inputQueue,
                                            final ExecutorService executor) {
        this.streamName = streamName;
        this.inputQueue = inputQueue;
        this.executor = executor;
    }


    @Override
    public void start() throws Exception {
        LOGGER.debug("action=start-speech-kinesis-producer stream={} is_running={}", streamName, isRunning);
        executor.execute(() -> {
                    try {
                        isRunning = true;
                        putData();
                    } catch (Exception e) {
                        isRunning = false;
                        LOGGER.warn("error=kinesis-producer-not-running stream={} error_msg={}", streamName, e.getMessage());
                        try {
                            Thread.sleep(5000L);
                        } catch (InterruptedException e1) {
                            LOGGER.warn("warning=speech-kinesis-producer-sleep-interrupted");
                        }
                        System.exit(1);
                    }
                }
        );
    }

    protected abstract void putData() throws Exception;

    @Override
    public  void stop() {
        isRunning = false;
        LOGGER.info("key=kinesis-producer-stopped stream={}", streamName);
    }

    public long recordsPut() {
        return recordsPut.get();
    }

    public void startRunning() {
        isRunning = true;
    }
}
