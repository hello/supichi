package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.AccountLocationDAO;
import com.hello.suripu.core.models.AccountLocation;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.models.AnnotatedTranscript;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;
import is.hello.speech.core.response.SupichiResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import is.hello.gaibu.weather.interfaces.WeatherReport;
import java.util.HashMap;
import java.util.Map;

public class WeatherHandler extends BaseHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherHandler.class);

    private static Map<String, SpeechCommand> commands;
    private final AccountLocationDAO accountLocationDAO;
    private final WeatherReport report;
    static {
        final Map<String, SpeechCommand> aMap = new HashMap<>();
        aMap.put("the weather", SpeechCommand.WEATHER);
        commands = ImmutableMap.copyOf(aMap);
    }


    private WeatherHandler(final SpeechCommandDAO speechCommandDAO, final WeatherReport report, final AccountLocationDAO accountLocationDAO) {
        super("weather", speechCommandDAO, commands);
        this.accountLocationDAO = accountLocationDAO;
        this.report = report;
    }


    public static WeatherHandler create(final SpeechCommandDAO speechCommandDAO, final WeatherReport report, final AccountLocationDAO accountLocationDAO) {
        return new WeatherHandler(speechCommandDAO, report, accountLocationDAO);
    }

    @Override
    public HandlerResult executeCommand(final String text, final String senseId, final Long accountId) {
        final Optional<AccountLocation> accountLocationOptional = accountLocationDAO.getLastLocationByAccountId(accountId);
        final Map<String,String> params = Maps.newHashMap();
        final String defaultText = "Weather information is not available at this time";
        params.put("text", defaultText);
        // 438 shotwell latitude: 37.761185, longitude: -122.416369

        if(accountLocationOptional.isPresent()) {
            LOGGER.info("action=get-location account_id={} result=found", accountId);
        }
        final Double latitude = accountLocationOptional.isPresent() ? accountLocationOptional.get().latitude : 37.761185;
        final Double longitude = accountLocationOptional.isPresent() ? accountLocationOptional.get().longitude : -122.416369;

        LOGGER.info("action=get-forecast account_id={} latitude={} longitude={}", accountId, latitude, longitude);

        final String summary = report.get(new Float(latitude), new Float(longitude), accountId, senseId);
        params.put("text", summary);
        LOGGER.info("action=get-forecast account_id={} result={}", accountId, summary);
        return new HandlerResult(HandlerType.WEATHER, SpeechCommand.WEATHER.getValue(), params);
    }

    @Override
    public Integer matchAnnotations(final AnnotatedTranscript annotatedTranscript) {
        // TODO: Location
        return NO_ANNOTATION_SCORE;
    }

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }
}
