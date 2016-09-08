package is.hello.speech.core.models;

/**
 * Created by ksg on 6/17/16
 */
public enum HandlerType {
    NONE("none"),
    SLEEP_SOUNDS("sleep_sounds"),
    ALARM("alarm"),
    HUE("hue"),
    NEST("nest"),
    ROOM_CONDITIONS("room_conditions"),
    TIME_REPORT("time_report"),
    TIMELINE("timeline"),
    TRIVIA("trivia"),
    WEATHER("weather"),
    RAKUTEN("rakuten"),
    RAKUTEN_JP("rakuten_jp");

    public String value;

    HandlerType(String value) { this.value = value; }
}
