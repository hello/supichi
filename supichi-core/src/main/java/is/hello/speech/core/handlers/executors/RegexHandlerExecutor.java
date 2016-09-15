package is.hello.speech.core.handlers.executors;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import is.hello.speech.core.handlers.BaseHandler;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.response.SupichiResponseType;

public class RegexHandlerExecutor implements HandlerExecutor {

    private Map<HandlerType, BaseHandler> availableHandlers = Maps.newConcurrentMap();
    private Map<String, HandlerType> commandToHandlerMap = Maps.newConcurrentMap();
    private Map<HandlerType, SupichiResponseType> responseBuilders = Maps.newConcurrentMap();

    private final static Logger LOGGER = LoggerFactory.getLogger(RegexHandlerExecutor.class);

    public RegexHandlerExecutor() {
    }


    @Override
    public HandlerResult handle(final String senseId, final Long accountId, final String transcript) {



        // TODO: command-parser
        final Optional<BaseHandler> optionalHandler = getHandler(transcript);

        if (optionalHandler.isPresent()) {
            final BaseHandler handler = optionalHandler.get();
            LOGGER.debug("action=find-handler result=success handler={}", handler.getClass().toString());

            final HandlerResult executeResult = handler.executeCommand(transcript, senseId, accountId);
            LOGGER.debug("action=execute-command result={}", executeResult.responseParameters.toString());
            return executeResult;
        }

        LOGGER.debug("action=fail-to-find-command account_id={} sense_id={} transcript={}", accountId, senseId, transcript);
        return HandlerResult.emptyResult();
    }

    @Override
    public HandlerExecutor register(final HandlerType handlerType, final BaseHandler baseHandler) {
        // Create in memory global map of commands
        for (final String command : baseHandler.getRelevantCommands()) {
            final HandlerType type = commandToHandlerMap.putIfAbsent(command, handlerType);
            if(type != null) {
                LOGGER.warn("warn=duplicate-command command={} handler={}", command, handlerType);
            }
        }

        availableHandlers.put(handlerType, baseHandler);
        responseBuilders.put(handlerType, baseHandler.responseType());
        return this;
    }

    @Override
    public Optional<BaseHandler> getHandler(String command) {
        for(final String pattern : commandToHandlerMap.keySet()) {
            final Pattern r = Pattern.compile(pattern);
            Matcher m = r.matcher(command);
            if(m.find()) {
                final HandlerType handlerType = commandToHandlerMap.get(pattern);
                if (availableHandlers.containsKey(handlerType)) {
                    return Optional.of(availableHandlers.get(handlerType));
                }
            }
        }
        return Optional.absent();
    }

    @Override
    public Map<HandlerType, SupichiResponseType> responseBuilders() {
        return responseBuilders;
    }
}