package is.hello.speech.core.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TextQuery {

    public final String senseId;
    public final Long accountId;
    public final String transcript;


    private TextQuery(String senseId, Long accountId, String transcript) {
        this.senseId = senseId;
        this.accountId = accountId;
        this.transcript = transcript;
    }

    @JsonCreator
    public static TextQuery create(
            @JsonProperty("sense_id") String senseId,
            @JsonProperty("account_id") Long accountId,
            @JsonProperty("transcript") String transcript) {
        return new TextQuery(senseId, accountId, transcript);
    }
}
