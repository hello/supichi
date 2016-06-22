package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.hello.suripu.core.models.sleep_sounds.Duration;
import com.hello.suripu.core.models.sleep_sounds.Sound;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.coredw8.clients.MessejiClient;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.models.SpeechCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;


/**
 * Created by ksg on 6/17/16
 */
public class SleepSoundHandler extends BaseHandler {

    // TODO: need to get these info from somewhere
    private static final Duration DEFAULT_SLEEP_SOUND_DURATION = Duration.create(2L, "30 Minutes", 1800);
    private static final Sound DEFAULT_SOUND = Sound.create(20L,
            "https://s3.amazonaws.com/hello-audio/sleep-tones-preview/Rainfall.mp3",
            "Rainfall",
            "/SLPTONES/ST006.RAW",
            "s3://hello-audio/sleep-tones-raw/2016-04-01/ST006.raw"
    );
    private static final long DEFAULT_SLEEP_SOUND_ID =  20L; //
    private static final Double SENSE_MAX_DECIBELS = 60.0;
    private static final int DEFAULT_SLEEP_SOUND_VOLUME_PERCENT = 70;

    // Fade in/out sounds over this many seconds on Sense
    private static final Integer FADE_IN = 1;
    private static final Integer FADE_OUT = 1; // Used when explicitly stopped with a Stop message or wave
    private static final Integer TIMEOUT_FADE_OUT = 20; // Used when sense's play duration times out


    private static final Logger LOGGER = LoggerFactory.getLogger(SleepSoundHandler.class);

    private final MessejiClient messejiClient;
    private final SleepSoundsProcessor sleepSoundsProcessor;

    public SleepSoundHandler(final MessejiClient messejiClient, final SpeechCommandDAO speechCommandDAO, final SleepSoundsProcessor sleepSoundsProcessor) {
        super("sleep_sound", speechCommandDAO, getAvailableActions());
        this.messejiClient = messejiClient;
        this.sleepSoundsProcessor = sleepSoundsProcessor;
    }


    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("okay play", SpeechCommand.SLEEP_SOUND_PLAY);
        tempMap.put("play sound", SpeechCommand.SLEEP_SOUND_PLAY);
        tempMap.put("play sleep sound", SpeechCommand.SLEEP_SOUND_PLAY);
        tempMap.put("play sleep", SpeechCommand.SLEEP_SOUND_PLAY);

        tempMap.put("stop", SpeechCommand.SLEEP_SOUND_STOP);
        tempMap.put("stop sound", SpeechCommand.SLEEP_SOUND_STOP);
        tempMap.put("stop sounds", SpeechCommand.SLEEP_SOUND_STOP);
        tempMap.put("stop sleep sounds", SpeechCommand.SLEEP_SOUND_STOP);
        tempMap.put("stop sleep sound", SpeechCommand.SLEEP_SOUND_STOP);
        tempMap.put("stopping sound", SpeechCommand.SLEEP_SOUND_STOP);
        return tempMap;
    }

    @Override
    public Boolean executionCommand(final String text, final String senseId, final Long accountId) {
        final Optional<SpeechCommand> command = getCommand(text);
        if (command.isPresent()) {
            if (command.get().equals(SpeechCommand.SLEEP_SOUND_PLAY)) {
                return playSleepSound(senseId, accountId);
            } else if (command.get().equals(SpeechCommand.SLEEP_SOUND_STOP)) {
                return stopSleepSound(senseId, accountId);
            }
        }
        return false;
    }

    private Boolean playSleepSound(final String senseId, final Long accountId) {

        // TODO: get most recently played sleep_sound_id, order, volume, etc...
        // final Optional<Sound> soundOptional = sleepSoundsProcessor.getSound(senseId, DEFAULT_SLEEP_SOUND_ID);
        final Optional<Sound> soundOptional = Optional.of(DEFAULT_SOUND);
        if (!soundOptional.isPresent()) {
            LOGGER.error("error=invalid-sound-id id={} sense_id={}", DEFAULT_SLEEP_SOUND_ID, senseId);
            return false;
        }

        final Integer volumeScalingFactor = convertToSenseVolumePercent(SENSE_MAX_DECIBELS, DEFAULT_SLEEP_SOUND_VOLUME_PERCENT);

        final Optional<Long> messageId = messejiClient.playAudio(
                senseId,
                MessejiClient.Sender.fromAccountId(accountId),
                System.nanoTime(),
                DEFAULT_SLEEP_SOUND_DURATION,
                soundOptional.get(),
                FADE_IN, FADE_OUT,
                volumeScalingFactor,
                TIMEOUT_FADE_OUT);

        if (!messageId.isPresent()) {
            LOGGER.error("error=messeji-request-play-audio-fail sense_id={} account_id={}", senseId, accountId);
            return false;
        }

        return true;
    }

    private Boolean stopSleepSound(final String senseId, final Long accountId) {
        final Optional<Long> messageId = messejiClient.stopAudio(
                senseId,
                MessejiClient.Sender.fromAccountId(accountId),
                System.nanoTime(),
                FADE_OUT);

        if (messageId.isPresent()) {
            return true;
        } else {
            LOGGER.error("error=messeji-request-stop-audio-fail sense_id={}, account_id={}", senseId, accountId);
            return false;
        }
    }

    private static Integer convertToSenseVolumePercent(final Double maxDecibels,
                                                       final Integer volumePercent) {
        if (volumePercent > 100 || volumePercent < 0) {
            throw new IllegalArgumentException(String.format("volumePercent must be in the range [0, 100], not %s", volumePercent));
        } else if (volumePercent <= 1) {
            return 0;
        }
        // Formula/constants obtained from http://www.sengpielaudio.com/calculator-loudness.htm
        final double decibelOffsetFromMaximum = 33.22 * Math.log10(volumePercent / 100.0);
        final double decibels = maxDecibels + decibelOffsetFromMaximum;
        return (int) Math.round((decibels / maxDecibels) * 100);
    }
}
