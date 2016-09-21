package is.hello.speech.core.models.entity;

/**
 * Created by ksg on 9/20/16
 */
public class VolumeEntity implements EntityInterface {
    private final String matchingText;
    private final Integer volumePercent;

    public VolumeEntity(final String matchingText, final Integer volumePercent) {
        this.matchingText = matchingText;
        this.volumePercent = volumePercent;
    }

    public Integer volume() { return this.volumePercent; }

    @Override
    public String matchingText() { return this.matchingText; }
}
