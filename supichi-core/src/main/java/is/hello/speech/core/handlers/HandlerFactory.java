package is.hello.speech.core.handlers;

import com.github.dvdme.ForecastIOLib.ForecastIO;
import com.hello.suripu.core.db.AccountLocationDAO;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.TimeZoneHistoryDAODynamoDB;
import com.hello.suripu.core.db.colors.SenseColorDAO;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.coredropwizard.clients.MessejiClient;
import is.hello.speech.core.db.SpeechCommandDAO;

/**
 * Created by ksg on 6/17/16
 */
public class HandlerFactory {

    final private SpeechCommandDAO speechCommandDAO;
    final private MessejiClient messejiClient;
    final private SleepSoundsProcessor sleepSoundsProcessor;
    final private DeviceDataDAODynamoDB deviceDataDAODynamoDB;
    final private DeviceDAO deviceDAO;
    final private SenseColorDAO senseColorDAO;
    final private CalibrationDAO calibrationDAO;
    final private TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB;
    final private String forecastio;
    final private AccountLocationDAO accountLocationDAO;


    private HandlerFactory(final SpeechCommandDAO speechCommandDAO,
                           final MessejiClient messejiClient,
                           final SleepSoundsProcessor sleepSoundsProcessor,
                           final DeviceDataDAODynamoDB deviceDataDAODynamoDB,
                           final DeviceDAO deviceDAO,
                           final SenseColorDAO senseColorDAO,
                           final CalibrationDAO calibrationDAO,
                           final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB,
                           final String forecastio,
                           final AccountLocationDAO accountLocationDAO) {
        this.speechCommandDAO = speechCommandDAO;
        this.messejiClient = messejiClient;
        this.sleepSoundsProcessor = sleepSoundsProcessor;
        this.deviceDataDAODynamoDB = deviceDataDAODynamoDB;
        this.deviceDAO = deviceDAO;
        this.senseColorDAO = senseColorDAO;
        this.calibrationDAO = calibrationDAO;
        this.timeZoneHistoryDAODynamoDB = timeZoneHistoryDAODynamoDB;
        this.forecastio = forecastio;
        this.accountLocationDAO = accountLocationDAO;
    }

    public static HandlerFactory create(final SpeechCommandDAO speechCommandDAO,
                                        final MessejiClient messejiClient,
                                        final SleepSoundsProcessor sleepSoundsProcessor,
                                        final DeviceDataDAODynamoDB deviceDataDAODynamoDB,
                                        final DeviceDAO deviceDAO,
                                        final SenseColorDAO senseColorDAO,
                                        final CalibrationDAO calibrationDAO,
                                        final TimeZoneHistoryDAODynamoDB timeZoneHistoryDAODynamoDB,
                                        final String forecastio,
                                        final AccountLocationDAO accountLocationDAO
                                        ) {

        return new HandlerFactory(speechCommandDAO, messejiClient, sleepSoundsProcessor, deviceDataDAODynamoDB,
                deviceDAO, senseColorDAO, calibrationDAO,timeZoneHistoryDAODynamoDB, forecastio, accountLocationDAO);
    }

    public WeatherHandler weatherHandler() {
        final ForecastIO forecastIOClient = new ForecastIO(forecastio);
        return WeatherHandler.create(speechCommandDAO, forecastIOClient, accountLocationDAO);
    }

    public AlarmHandler alarmHandler() {
        return new AlarmHandler(speechCommandDAO);
    }

    public TimeHandler timeHandler() {
        return new TimeHandler(speechCommandDAO, timeZoneHistoryDAODynamoDB);
    }

    public TriviaHandler triviaHandler() {
        return new TriviaHandler(speechCommandDAO);
    }

    public RoomConditionsHandler roomConditionsHandler() {
        return new RoomConditionsHandler(speechCommandDAO, deviceDataDAODynamoDB,deviceDAO,senseColorDAO,calibrationDAO);
    }

    public SleepSoundHandler sleepSoundHandler() {
        return new SleepSoundHandler(messejiClient, speechCommandDAO, sleepSoundsProcessor,5);
    }

    public HueHandler hueHandler() {
        return new HueHandler(speechCommandDAO);
    }
}
