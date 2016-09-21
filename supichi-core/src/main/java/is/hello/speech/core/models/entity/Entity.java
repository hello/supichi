package is.hello.speech.core.models.entity;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * Created by ksg on 9/20/16
 */
public class Entity {

    public final String transcript;

    public final List<TimeEntity> times;

    public final List<DurationEntity> durations;

    public final List<SleepSoundEntity> sleepSounds;

    public final List<VolumeEntity> volumes;

    // future entities
    // temperature
    // Hue patterns
    // Nest patterns
    // location
    // house location

    public Entity(final String transcript, final List<TimeEntity> times, final List<DurationEntity> durations, final List<SleepSoundEntity> sleepSounds, final List<VolumeEntity> volumes) {
        this.transcript = transcript;
        this.times = times;
        this.durations = durations;
        this.sleepSounds = sleepSounds;
        this.volumes = volumes;
    }

    public static class Builder {
        private String transcript = "";
        private List<TimeEntity> times = Lists.newArrayList();
        private List<DurationEntity> durations = Lists.newArrayList();
        private List<SleepSoundEntity> sleepSounds = Lists.newArrayList();
        private List<VolumeEntity> volumes = Lists.newArrayList();

        public Builder withTranscript(final String text) {
            this.transcript = text;
            return this;
        }

        public Builder withTimes(final List<TimeEntity> times) {
            this.times.addAll(times);
            return this;
        }

        public Builder withDurations(final List<DurationEntity> durations) {
            this.durations.addAll(durations);
            return this;
        }

        public Builder withSleepSounds(final List<SleepSoundEntity> sounds) {
            this.sleepSounds.addAll(sounds);
            return this;
        }

        public Builder withVolumes(final List<VolumeEntity> volumes) {
            this.volumes.addAll(volumes);
            return this;
        }

        public Entity build() {
            return new Entity(transcript, times, durations, sleepSounds, volumes);
        }
    }
}
