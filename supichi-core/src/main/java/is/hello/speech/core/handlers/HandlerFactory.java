package is.hello.speech.core.handlers;

import com.github.dvdme.ForecastIOLib.ForecastIO;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.AccountLocationDAO;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.db.colors.SenseColorDAO;
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

    public static HandlerFactory create(final SpeechCommandDAO speechCommandDAO,
                                        final MessejiClient messejiClient,
                                        final SleepSoundsProcessor sleepSoundsProcessor,
                                        final DeviceDataDAODynamoDB deviceDataDAODynamoDB,
                                        final DeviceDAO deviceDAO,
                                        final SenseColorDAO senseColorDAO,
                                        final CalibrationDAO calibrationDAO,
                                        final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB,
                                        final String forecastio,
                                        final AccountLocationDAO accountLocationDAO
                                        ) {

        final Map<HandlerType, BaseHandler> handlerMap = Maps.newHashMap();

        // create handlers

        // sleep sounds
        final SleepSoundHandler sleepSoundHandler = new SleepSoundHandler(messejiClient, speechCommandDAO, sleepSoundsProcessor);
        handlerMap.put(HandlerType.SLEEP_SOUNDS, sleepSoundHandler);

        // room conditions
        final RoomConditionsHandler roomConditionsHandler = new RoomConditionsHandler(speechCommandDAO, deviceDataDAODynamoDB, deviceDAO, senseColorDAO, calibrationDAO);
        handlerMap.put(HandlerType.ROOM_CONDITIONS, roomConditionsHandler);

        // current time
        final TimeHandler timeHandler = new TimeHandler(speechCommandDAO, timeZoneHistoryDAODynamoDB);
        handlerMap.put(HandlerType.TIME_REPORT, timeHandler);

        // trivia
        final TriviaHandler triviaHandler = new TriviaHandler(speechCommandDAO);
        handlerMap.put(HandlerType.TRIVIA, triviaHandler);

        // TODO: alarm
        final AlarmHandler alarmHandler = new AlarmHandler(speechCommandDAO);
        handlerMap.put(HandlerType.ALARM, alarmHandler);

        final ForecastIO forecastIOClient = new ForecastIO(forecastio);
        final WeatherHandler weatherHandler = WeatherHandler.create(speechCommandDAO, forecastIOClient, accountLocationDAO);
        handlerMap.put(HandlerType.WEATHER, weatherHandler);

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

    public WeatherHandler weatherHandler() {
        return (WeatherHandler) availableHandlers.get(HandlerType.WEATHER);
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
