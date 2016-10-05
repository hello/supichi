package is.hello.speech.core.executors;

import is.hello.speech.core.handlers.BaseHandler;
import is.hello.speech.core.models.VoiceRequest;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.response.SupichiResponseType;

import java.util.Map;

public interface HandlerExecutor {
    HandlerResult handle(final VoiceRequest request);
    HandlerExecutor register(HandlerType handlerType, BaseHandler baseHandler);
    Map<HandlerType, SupichiResponseType> responseBuilders();
}
