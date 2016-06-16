package is.hello.speech.clients;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeechClientManaged implements Managed {

    private final static Logger LOGGER = LoggerFactory.getLogger(SpeechClientManaged.class);

    private final AsyncSpeechClient asyncSpeechClient;

    public SpeechClientManaged(AsyncSpeechClient asyncSpeechClient) {
        this.asyncSpeechClient = asyncSpeechClient;
    }

    @Override
    public void start() throws Exception {
    }

    @Override
    public void stop() throws Exception {
        LOGGER.warn("Shutting down");
        asyncSpeechClient.shutdown();
        LOGGER.warn("Done.");
    }
}
