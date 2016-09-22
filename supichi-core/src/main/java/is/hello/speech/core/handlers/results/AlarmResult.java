package is.hello.speech.core.handlers.results;

import com.google.common.base.Optional;

/**
 * Created by ksg on 9/21/16
 */
public class AlarmResult implements ResultInterface {

    public final Outcome outcome;
    public final Optional<String> errorText;
    public final Optional<String> responseText;

    public AlarmResult(final Outcome outcome, final Optional<String> errorText, final Optional<String> responseText) {
        this.outcome = outcome;
        this.errorText = errorText;
        this.responseText = responseText;
    }


    @Override
    public String responseText() {
        if (responseText.isPresent()) {
            return responseText.get();
        }
        return "";
    }

    @Override
    public String commandText() {
        return null;
    }
}
