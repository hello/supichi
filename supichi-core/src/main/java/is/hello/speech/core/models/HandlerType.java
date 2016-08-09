package is.hello.speech.core.models;

/**
 * Created by ksg on 6/17/16
 */
public enum HandlerType {
    NONE("none"),
    SLEEP_SOUNDS("sleep_sounds"),
    ALARM("alarm"),
    ROOM_CONDITIONS("room_conditions"),
    TIME_REPORT("time_report"),
    TRIVIA("trivia"),
    WOLFRAM_ALPHA("wolfram_alpha"),
    WEATHER("weather");

    public String value;

    HandlerType(String value) { this.value = value; }
}
