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
import is.hello.speech.core.models.VoiceRequest;
import is.hello.speech.core.response.SupichiResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import is.hello.gaibu.weather.interfaces.WeatherReport;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        final String text = annotatedTranscript.transcript;

        final Optional<AccountLocation> accountLocationOptional = accountLocationDAO.getLastLocationByAccountId(request.accountId);
        final Map<String,String> params = Maps.newHashMap();
        final String defaultText = "Weather information is not available at this time";
        params.put("text", defaultText);
        // 438 shotwell latitude: 37.761185, longitude: -122.416369
        LOGGER.info("action=weather-handler-execute account_id={} sense_id={} ip_address={}", request.accountId, request.senseId, request.ipAddress);

        if(accountLocationOptional.isPresent()) {
            LOGGER.info("action=get-location account_id={} result=found", request.accountId);
            final Double latitude = accountLocationOptional.isPresent() ? accountLocationOptional.get().latitude : 37.761185;
            final Double longitude = accountLocationOptional.isPresent() ? accountLocationOptional.get().longitude : -122.416369;

            LOGGER.info("action=get-forecast account_id={} latitude={} longitude={}", request.accountId, latitude, longitude);

            final String summary = report.get(latitude, longitude, request.accountId, request.senseId);
            LOGGER.info("action=get-forecast account_id={} result={}", request.accountId, summary);
            params.put("text", summary);
        } else {
            try {
                final String summary = report.get(request.accountId, request.senseId, InetAddress.getByName(request.ipAddress));
                LOGGER.info("action=get-forecast account_id={} result={}", request.accountId, summary);
                params.put("text", summary);
            } catch (UnknownHostException e) {
                LOGGER.info("error=get-forecast account_id={} msg={}", request.accountId, e.getMessage());
                params.put("text", "Server error.");
            }
        }



        return new HandlerResult(HandlerType.WEATHER, SpeechCommand.WEATHER.getValue(), params, Optional.absent());
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
