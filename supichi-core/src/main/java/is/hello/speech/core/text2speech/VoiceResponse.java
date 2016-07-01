package is.hello.speech.core.text2speech;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Created by ksg on 6/29/16
 */
public class VoiceResponse {

    public enum ServiceType {
        WATSON(1);
        protected int value;
        ServiceType(final int value) { this.value = value; }
        public int getValue() { return this.value; }
    }

    public enum VoiceType {
        ALLISON(1);
        protected int value;
        VoiceType(final int value) { this.value = value; }
        public int getValue() { return this.value; }
    }

    public enum ResponseType {
        SUCCESS(1),
        FAILURE(2),
        IDK(3),
        TIMEOUT(4),
        NETWORK_ERROR(5),
        API_ERROR(6),
        UNKNOWN_ERROR(7);

        protected int value;
        ResponseType(final int value) { this.value = value; }
        public int getValue() { return this.value; }
    }

    public final String text;
    public final Intention.IntentType intent;
    public final Intention.ActionType action;
    public final Intention.IntentCategory category;
    public final String parameters;
    public final ServiceType serviceType;
    public final VoiceType voiceType;
    public final ResponseType responseType;

    @JsonCreator
    public VoiceResponse(
            @JsonProperty("text") String text,
            @JsonProperty("intent") Intention.IntentType intent,
            @JsonProperty("action") Intention.ActionType action,
            @JsonProperty("category") Intention.IntentCategory category,
            @JsonProperty("parameters") String parameters,
            @JsonProperty("service_type") ServiceType serviceType,
            @JsonProperty("voice_type") VoiceType voiceType,
            @JsonProperty("response_type") ResponseType responseType) {
        this.text = text;
        this.intent = intent;
        this.action = action;
        this.category = category;
        this.parameters = parameters;
        this.serviceType = serviceType;
        this.voiceType = voiceType;
        this.responseType = responseType;
    }
}
