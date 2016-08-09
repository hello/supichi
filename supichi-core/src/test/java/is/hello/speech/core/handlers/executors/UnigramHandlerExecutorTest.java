package is.hello.speech.core.handlers.executors;

import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.coredw8.clients.MessejiClient;
import is.hello.speech.core.db.SpeechCommandDAO;
import org.junit.Test;

import static org.mockito.Mockito.mock;

public class UnigramHandlerExecutorTest {

    @Test
    public void TestHandleEmptyTranscript() {

        final SpeechCommandDAO speechCommandDAO = mock(SpeechCommandDAO.class);
        final MessejiClient client = mock(MessejiClient.class);
        final SleepSoundsProcessor sleepSoundsProcessor = mock(SleepSoundsProcessor.class);

//        HandlerFactory factory = HandlerFactory.create();
//        UnigramHandlerExecutor executor = new UnigramHandlerExecutor()

    }

}
