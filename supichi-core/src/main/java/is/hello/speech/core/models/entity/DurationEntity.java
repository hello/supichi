package is.hello.speech.core.models.entity;

import org.joda.time.Duration;


/**
 * Created by ksg on 9/20/16
 */
public class DurationEntity implements EntityInterface {

    private final String matchingText;
    private final Duration duration;

    public DurationEntity(final String matchingText, final Duration duration) {
        this.matchingText = matchingText;
        this.duration = duration;
    }

    public Duration duration() { return this.duration; }

    @Override
    public String matchingText() {
        return this.matchingText;
    }
}
