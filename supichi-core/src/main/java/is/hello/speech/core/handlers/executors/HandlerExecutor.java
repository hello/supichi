package is.hello.speech.core.handlers.executors;

import com.google.common.base.Optional;
import is.hello.speech.core.handlers.BaseHandler;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;

public interface HandlerExecutor {
    HandlerResult handle(String senseId, Long accountId, String transcript);
    HandlerExecutor register(HandlerType handlerType, BaseHandler baseHandler);
    Optional<BaseHandler> getHandler(String command);
}
