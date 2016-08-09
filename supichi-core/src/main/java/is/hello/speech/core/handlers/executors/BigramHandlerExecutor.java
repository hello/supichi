package is.hello.speech.core.handlers.executors;

import com.google.common.base.Optional;
import is.hello.speech.core.handlers.BaseHandler;
import is.hello.speech.core.handlers.HandlerFactory;
import is.hello.speech.core.models.HandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BigramHandlerExecutor implements HandlerExecutor {

    private final static Logger LOGGER = LoggerFactory.getLogger(BigramHandlerExecutor.class);

    private final HandlerFactory factory;

    public BigramHandlerExecutor(final HandlerFactory factory) {
        this.factory = factory;
    }


    @Override
    public HandlerResult handle(final String senseId, final Long accountId, final String transcript) {
        final String[] unigrams = transcript.toLowerCase().split(" ");

        for (int i = 0; i < (unigrams.length - 1); i++) {
            final String commandText = String.format("%s %s", unigrams[i], unigrams[i+1]);
            LOGGER.debug("action=get-transcribed-command text={}", commandText);

            // TODO: command-parser
            final Optional<BaseHandler> optionalHandler = factory.getHandler(commandText);

            if (optionalHandler.isPresent()) {
                final BaseHandler handler = optionalHandler.get();
                LOGGER.debug("action=find-handler result=success handler={}", handler.getClass().toString());

                final HandlerResult executeResult = handler.executeCommand(commandText, senseId, accountId);
                LOGGER.debug("action=execute-command result={}", executeResult);
                return executeResult;
            }
        }

        LOGGER.debug("action=fail-to-find-command account_id={} sense_id={} transcript={}", accountId, senseId, transcript);
        return HandlerResult.emptyResult();
    }
}
