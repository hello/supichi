package is.hello.speech.core.handlers.executors;

import is.hello.speech.core.handlers.BaseHandler;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.response.SupichiResponseType;

import java.util.Map;

public interface HandlerExecutor {
    HandlerResult handle(String senseId, Long accountId, String transcript);
    HandlerExecutor register(HandlerType handlerType, BaseHandler baseHandler);
    Map<HandlerType, SupichiResponseType> responseBuilders();
}
