package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.coredw8.clients.MessejiClient;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.models.HandlerType;

import java.util.Map;
import java.util.Set;

/**
 * Created by ksg on 6/17/16
 */
public class HandlerFactory {

    private ImmutableMap<HandlerType, BaseHandler> availableHandlers;
    private Map<String, HandlerType> commandToHandlerMap;

    private HandlerFactory(final Map<HandlerType, BaseHandler> availableHandlers,
                           final Map<String, HandlerType> commandToHandlerMap) {
        this.availableHandlers = ImmutableMap.copyOf(availableHandlers);
        this.commandToHandlerMap = commandToHandlerMap;
    }

    public static HandlerFactory create(final SpeechCommandDAO speechCommandDAO, final MessejiClient messejiClient, final SleepSoundsProcessor sleepSoundsProcessor) {

        final Map<HandlerType, BaseHandler> handlerMap = Maps.newHashMap();

        // create handlers
        // sleep sounds
        final SleepSoundHandler sleepSoundHandler = new SleepSoundHandler(messejiClient, speechCommandDAO, sleepSoundsProcessor);
        handlerMap.put(HandlerType.SLEEP_SOUNDS, sleepSoundHandler);

        // Alarm
        final AlarmHandler alarmHandler = new AlarmHandler(speechCommandDAO);
        handlerMap.put(HandlerType.ALARM, alarmHandler);

        // map command text to handler
        final Map<String, HandlerType> commandToHandlerMap = Maps.newHashMap();
        for (Map.Entry<HandlerType, BaseHandler> entrySet : handlerMap.entrySet()) {
            final Set<String> commands = entrySet.getValue().getRelevantCommands();
            final HandlerType handlerType = entrySet.getKey();
            for (String command : commands) {
                commandToHandlerMap.put(command, handlerType);
            }
        }

        return new HandlerFactory(handlerMap, commandToHandlerMap);
    }

    public Optional<BaseHandler> getHandler(final String command) {
        if (commandToHandlerMap.containsKey(command)) {
            final HandlerType handlerType = commandToHandlerMap.get(command);
            if (availableHandlers.containsKey(handlerType)) {
                return Optional.of(availableHandlers.get(handlerType));
            }
        }
        return Optional.absent();
    }


}
