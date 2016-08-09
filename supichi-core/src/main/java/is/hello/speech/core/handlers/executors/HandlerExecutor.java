package is.hello.speech.core.handlers.executors;

import is.hello.speech.core.models.HandlerResult;

public interface HandlerExecutor {
    HandlerResult handle(String senseId, Long accountId, String transcript);
}
