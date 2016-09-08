package is.hello.speech.core.models;

import is.hello.speech.core.handlers.AlarmHandler;
import is.hello.speech.core.handlers.HueHandler;
import is.hello.speech.core.handlers.NestHandler;
import is.hello.speech.core.handlers.RakutenHandler;
import is.hello.speech.core.handlers.RakutenJPHandler;
import is.hello.speech.core.handlers.RoomConditionsHandler;
import is.hello.speech.core.handlers.SleepSoundHandler;
import is.hello.speech.core.handlers.TimeHandler;
import is.hello.speech.core.handlers.TimelineHandler;
import is.hello.speech.core.handlers.TriviaHandler;
import is.hello.speech.core.handlers.WeatherHandler;

/**
 * Created by ksg on 6/21/16
 */
public enum SpeechCommand {
    SLEEP_SOUND_PLAY("sleep_sound_play", SleepSoundHandler.class),
    SLEEP_SOUND_STOP("sleep_sound_stop", SleepSoundHandler.class),
    ALARM_SET("alarm_set", AlarmHandler.class),
    ALARM_DELETE("alarm_delete", AlarmHandler.class),
    LIGHT_SET("light_set", HueHandler.class),
    LIGHT_TOGGLE("light_toggle", HueHandler.class),
    ROOM_TEMPERATURE("room_temperature", RoomConditionsHandler.class),
    ROOM_HUMIDITY("room_humidity", RoomConditionsHandler.class),
    ROOM_LIGHT("room_light", RoomConditionsHandler.class),
    ROOM_SOUND("room_sound", RoomConditionsHandler.class),
    PARTICULATES("particulates", RoomConditionsHandler.class),
    THERMOSTAT_READ("thermostat_read", NestHandler.class),
    THERMOSTAT_SET("thermostat_set", NestHandler.class),
    THERMOSTAT_ACTIVE("thermostat_active", NestHandler.class),
    TIME_REPORT("time_report",TimeHandler.class),
    TRIVIA("trivia", TriviaHandler.class),
    WEATHER("weather", WeatherHandler.class),
    TIMELINE("timeline",TimelineHandler.class),
    RAKUTEN_JP("rakuten_jp", RakutenJPHandler.class),
    RAKUTEN("rakuten", RakutenHandler.class);

    private String value;
    private Class commandClass;

    SpeechCommand(String value, Class commandClass) {
        this.value = value;
        this.commandClass = commandClass;
    }

    public String getValue() { return value; }
    public Class getCommandClass() { return commandClass; }
}