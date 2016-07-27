package is.hello.speech.core.db;

import com.amazonaws.services.dynamodbv2.xspec.M;
import is.hello.speech.core.models.HandlerType;

import java.util.List;
import java.util.Map;

/**
 * Created by ksg on 6/20/16
 */
public interface SpeechCommandDAO {
    //Map<String, Action> getActionCommands(HandlerType handlerType);
    List<String> getHandlerCommands(HandlerType handlerType);
}
