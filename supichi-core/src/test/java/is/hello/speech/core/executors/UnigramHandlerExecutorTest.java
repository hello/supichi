package is.hello.speech.core.executors;

import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.handlers.TriviaHandler;
import is.hello.speech.core.models.VoiceRequest;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class UnigramHandlerExecutorTest {

    @Test
    public void TestHandleEmptyHandler() {

        final UnigramHandlerExecutor executor = new UnigramHandlerExecutor();
        final HandlerResult result = executor.handle(new VoiceRequest("123456789", 99L, "whatever", ""));
        assertEquals(result.handlerType, HandlerType.NONE);
    }

    @Test
    public void TestHandleSingleHandler() {

        final SpeechCommandDAO speechCommandDAO = mock(SpeechCommandDAO.class);
        final TriviaHandler handler = new TriviaHandler(speechCommandDAO);
        final HandlerExecutor executor = new UnigramHandlerExecutor()
                .register(HandlerType.TRIVIA, handler);

        final HandlerResult correctResult = executor.handle(new VoiceRequest("123456789", 99L, "the president", ""));
        assertEquals(correctResult.handlerType, HandlerType.TRIVIA);

        final HandlerResult result = executor.handle(new VoiceRequest("123456789", 99L, "whatever", ""));
        assertEquals(result.handlerType, HandlerType.NONE);
    }

}
