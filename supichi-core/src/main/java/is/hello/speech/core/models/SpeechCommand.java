package is.hello.speech.core.models;

import is.hello.speech.core.handlers.AlarmHandler;
import is.hello.speech.core.handlers.RoomConditionsHandler;
import is.hello.speech.core.handlers.SleepSoundHandler;

/**
 * Created by ksg on 6/21/16
 */
public enum SpeechCommand {
    SLEEP_SOUND_PLAY("sleep_sound_play", SleepSoundHandler.class),
    SLEEP_SOUND_STOP("sleep_sound_stop", SleepSoundHandler.class),
    ALARM_SET("alarm_set", AlarmHandler.class),
    ALARM_DELETE("alarm_delete", AlarmHandler.class),
    ROOM_TEMPERATURE("room_temperature", RoomConditionsHandler.class),
    ROOM_HUMIDITY("room_humidity", RoomConditionsHandler.class),
    ROOM_LIGHT("room_light", RoomConditionsHandler.class),
    ROOM_SOUND("room_sound", RoomConditionsHandler.class);

    private String value;
    private Class commandClass;

    SpeechCommand(String value, Class commandClass) {
        this.value = value;
        this.commandClass = commandClass;
    }

    public String getValue() { return value; }
    public Class getCommandClass() { return commandClass; }
}
