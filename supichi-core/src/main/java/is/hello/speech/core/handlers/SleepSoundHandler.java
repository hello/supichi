package is.hello.speech.core.handlers;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hello.suripu.coredw8.clients.MessejiClient;
import is.hello.speech.core.db.SpeechCommandDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;


/**
 * Created by ksg on 6/17/16
 */
public class SleepSoundHandler implements BaseHandler {
    private enum SleepSoundCommand {
        NONE("none"),
        PLAY("play"),
        STOP("stop");

        private String value;

        SleepSoundCommand(String value) {
            this.value = value;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SleepSoundHandler.class);

    private final MessejiClient messejiClient;
    private final SpeechCommandDAO speechCommandDAO;
    private final ImmutableMap<String, SleepSoundCommand> commandMap;

    public SleepSoundHandler(final MessejiClient messejiClient, final SpeechCommandDAO speechCommandDAO) {
        this.messejiClient = messejiClient;
        this.speechCommandDAO = speechCommandDAO;
        this.commandMap =  ImmutableMap.copyOf(getAvailableActions());
    }

    private static Map<String, SleepSoundCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SleepSoundCommand> tempMap = Maps.newHashMap();
        tempMap.put("play", SleepSoundCommand.PLAY);
        tempMap.put("play sound", SleepSoundCommand.PLAY);
        tempMap.put("play sleep sound", SleepSoundCommand.PLAY);
        tempMap.put("stop", SleepSoundCommand.STOP);
        tempMap.put("stop sound", SleepSoundCommand.STOP);
        return tempMap;
    }

    @Override
    public Set<String> getRelevantCommands() {
        return this.commandMap.keySet();
    }

    @Override
    public Boolean executionCommand(final String text, final String senseId) {
        final SleepSoundCommand command = getCommand(text);

        if (command.equals(SleepSoundCommand.NONE)) {
            return false;
        }

        if (command.equals(SleepSoundCommand.PLAY)) {
            return playSleepSound(senseId);
        }

        return stopSleepSound(senseId);

    }

    private SleepSoundCommand getCommand(final String text) {
        if (commandMap.containsKey(text)) {
            return commandMap.get(text);
        }
        return SleepSoundCommand.NONE;
    }

    private Boolean playSleepSound(final String senseId) {
        // TODO:
        return false;
    }

    private Boolean stopSleepSound(final String senseId) {
        // TODO:
        return false;
    }

}
