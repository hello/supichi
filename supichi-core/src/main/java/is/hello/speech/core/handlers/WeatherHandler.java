package is.hello.speech.core.handlers;

import com.github.dvdme.ForecastIOLib.FIODaily;
import com.github.dvdme.ForecastIOLib.ForecastIO;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.AccountLocationDAO;
import com.hello.suripu.core.models.AccountLocation;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;

import java.util.HashMap;
import java.util.Map;

public class WeatherHandler extends BaseHandler {

    private static Map<String, SpeechCommand> commands;
    private final AccountLocationDAO accountLocationDAO;
    private final ForecastIO forecastIO;
    static {
        final Map<String, SpeechCommand> aMap = new HashMap<>();
        aMap.put("the weather", SpeechCommand.WEATHER);
        commands = ImmutableMap.copyOf(aMap);
    }


    private WeatherHandler(final SpeechCommandDAO speechCommandDAO, final ForecastIO forecastIO, final AccountLocationDAO accountLocationDAO) {
        super("weather", speechCommandDAO, commands);
        this.accountLocationDAO = accountLocationDAO;
        this.forecastIO = forecastIO;
    }


    public static WeatherHandler create(final SpeechCommandDAO speechCommandDAO, final ForecastIO forecastIO, final AccountLocationDAO accountLocationDAO) {
        return new WeatherHandler(speechCommandDAO, forecastIO, accountLocationDAO);
    }

    @Override
    public HandlerResult executeCommand(final String text, final String senseId, final Long accountId) {
        final Optional<AccountLocation> accountLocationOptional = accountLocationDAO.getLastLocationByAccountId(accountId);
        final Map<String,String> params = Maps.newHashMap();
        params.put("text", "Weather information is not available at this time");
        // 438 shotwell latitude: 37.761185, longitude: -122.416369

        final Double latitude = accountLocationOptional.isPresent() ? accountLocationOptional.get().latitude : 37.761185;
        final Double longitude = accountLocationOptional.isPresent() ? accountLocationOptional.get().longitude : -122.416369;

        // WARNING: THIS IS A TERRIBLE LIBRARY AND IS NOT THREADSAFE
        final boolean success = forecastIO.getForecast(String.valueOf(latitude), String.valueOf(longitude));
        if(success) {

            final FIODaily daily = new FIODaily(forecastIO);
            params.put("text", String.format("The weather is: %s", daily.getDay(0).summary()));
            return new HandlerResult(HandlerType.WEATHER, params);
        }

        return new HandlerResult(HandlerType.WEATHER, params);
    }

//    ForecastIO fio = new ForecastIO(your_api_key); //instantiate the class with the API key.
//    fio.setUnits(ForecastIO.UNITS_SI);             //sets the units as SI - optional
//    fio.setExcludeURL("hourly,minutely");             //excluded the minutely and hourly reports from the reply
//    fio.getForecast("38.7252993", "-9.1500364");


}
