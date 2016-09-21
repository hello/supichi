package is.hello.speech.core.models;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import is.hello.speech.core.handlers.SleepSoundHandler;
import is.hello.speech.core.models.annotations.DurationAnnotation;
import is.hello.speech.core.models.annotations.SleepSoundAnnotation;
import is.hello.speech.core.models.annotations.TimeAnnotation;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Created by ksg on 9/20/16
 */
public class Annotator {
    private final static Logger LOGGER = LoggerFactory.getLogger(Annotator.class);

    private static class TimeDurations {
        public final List<TimeAnnotation> times;
        public final List<DurationAnnotation> durations;

        public TimeDurations(final List<TimeAnnotation> times, final List<DurationAnnotation> durations) {
            this.times = times;
            this.durations = durations;
        }
    }

    public static AnnotatedTranscript get(final String text, final Optional<TimeZone> timezone) {

        final AnnotatedTranscript.Builder builder = new AnnotatedTranscript.Builder()
                .withTranscript(text)
                .withSleepSounds(getSleepSounds(text.toLowerCase()));

        if (timezone.isPresent()) {
            final TimeDurations timeDurations = getTimeDurations(text.toLowerCase(), timezone.get());
            builder.withTimes(timeDurations.times)
                    .withDurations(timeDurations.durations);
        }

        return builder.build();
    }

    private static List<SleepSoundAnnotation> getSleepSounds(final String text) {
        final List<SleepSoundAnnotation> entities = Lists.newArrayList();
        for (final SleepSoundHandler.SoundName soundName : SleepSoundHandler.SoundName.values()) {
            if (text.contains(soundName.value)) {
                entities.add(new SleepSoundAnnotation(soundName.value, soundName));
            }
        }
        return entities;
    }

    private static TimeDurations getTimeDurations(final String text, final TimeZone timezone) {
        // final Parser timeParser = new Parser(DateTimeZone.forID("America/Los_Angeles").toTimeZone());
        final Parser timeParser = new Parser(timezone);
        final List<TimeAnnotation> entities = Lists.newArrayList();
        final List<DurationAnnotation> durationEntities = Lists.newArrayList();

        final List<DateGroup> groups = timeParser.parse(text);
        for (final DateGroup group:groups) {
            final List<Date> dates = group.getDates();
            if (dates.size() == 2) {
                // duration
                final org.joda.time.Duration duration = new org.joda.time.Duration(new DateTime(dates.get(0)), new DateTime(dates.get(1)));
                durationEntities.add(new DurationAnnotation(group.getText(), duration));
            } else {
                for (final Date date : dates) {
                    final String matchingValue = group.getText();
                    final DateTime dateTime = new DateTime(date.getTime()); // local utc ts
                    LOGGER.debug("action=parse_time text={} date={} matched_string={} datetime={}",
                            text, date, matchingValue, dateTime);
                    entities.add(new TimeAnnotation(matchingValue, dateTime));
                }
            }
        }
        return new TimeDurations(entities, durationEntities);
    }
}
