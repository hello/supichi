package is.hello.speech.workers.text2speech;

/**
 * Created by ksg on 6/29/16
 */
public class Intention {
    public enum IntentType {
        SLEEP_SOUNDS ("sleep_sounds"),
        ALARM("alarm"),
        SLEEP_REPORT("sleep_report"),
        ROOM_CONDITIONS("room_conditions");

        protected String value;

        IntentType(final String value) { this.value = value; }
    }

    public enum ActionType {
        PLAY_SOUND("play_sound"),
        STOP_SOUND("stop_sound");
        protected String value;

        ActionType(final String value) { this.value = value; }
    }

    public enum IntentCategory {
        DEFAULT("default"),
        SOUND_NAME("sound_name"), // sleep-sounds
        SOUND_DURATION("sound_duration"),
        SOUND_NAME_DURATION("sound_name_duration");

        protected String value;

        IntentCategory(final String value) { this.value = value; }
    }
}
