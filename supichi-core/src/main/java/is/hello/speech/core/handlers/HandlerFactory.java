package is.hello.speech.core.handlers;

import com.google.common.collect.ImmutableMap;
import is.hello.speech.core.models.HandlerType;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Set;

/**
 * Created by ksg on 6/17/16
 */
public class HandlerFactory {

    private ImmutableMap<HandlerType, BaseHandler> availableHandlers;
    private Map<String, HandlerType> textToHandlerMap;

    public HandlerFactory(ImmutableMap<HandlerType, BaseHandler> availableHandlers) {
        this.availableHandlers = availableHandlers;
        this.textToHandlerMap = Maps.newHashMap();
        for (Map.Entry<HandlerType, BaseHandler> entrySet : availableHandlers.entrySet()) {
            final Set<String> commands = entrySet.getValue().getRelevantCommands();
            final HandlerType handlerType = entrySet.getKey();
            for (String command : commands) {
                textToHandlerMap.put(command, handlerType);
            }
        }
    }

    public Optional<BaseHandler> getHandler(final String command) {
        if (textToHandlerMap.containsKey(command)) {
            final HandlerType handlerType = textToHandlerMap.get(command);
            if (availableHandlers.containsKey(handlerType)) {
                return Optional.of(availableHandlers.get(handlerType));
            }
        }
        return Optional.absent();
    }


}
