package is.hello.speech.core.models.entity;

import com.hello.suripu.core.models.sleep_sounds.Sound;
import is.hello.speech.core.handlers.SleepSoundHandler;

/**
 * Created by ksg on 9/20/16
 */
public class SleepSoundEntity implements EntityInterface {

    private final String matchingText;
    private final SleepSoundHandler.SoundName soundName;

    public SleepSoundEntity(final String matchingText, final SleepSoundHandler.SoundName soundName) {
        this.matchingText = matchingText;
        this.soundName = soundName;
    }

    public SleepSoundHandler.SoundName sound() { return this.soundName; }

    @Override
    public String matchingText() {
        return this.matchingText;
    }

    public static Sound getSound(final String name) {
        final Long id;
        final String s3Url;
        final String soundName;
        final String path;
        final String s3Key;
        switch (name.toLowerCase()) {
            case "rainfall":
                id = 20L;
                s3Url = "https://s3.amazonaws.com/hello-audio/sleep-tones-preview/Rainfall.mp3";
                soundName = "Rainfall";
                path = "/SLPTONES/ST006.RAW";
                s3Key = "s3://hello-audio/sleep-tones-raw/2016-04-01/ST006.raw";
                break;
            default:
                id = 20L;
                s3Url = "https://s3.amazonaws.com/hello-audio/sleep-tones-preview/Rainfall.mp3";
                soundName = "Rainfall";
                path = "/SLPTONES/ST006.RAW";
                s3Key = "s3://hello-audio/sleep-tones-raw/2016-04-01/ST006.raw";

        }

        return Sound.create(id, s3Url, soundName, path, s3Key);
    }
}
