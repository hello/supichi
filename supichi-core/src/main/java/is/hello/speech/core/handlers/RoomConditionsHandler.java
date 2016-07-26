package is.hello.speech.core.handlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.colors.SenseColorDAO;
import com.hello.suripu.core.models.Calibration;
import com.hello.suripu.core.models.CurrentRoomState;
import com.hello.suripu.core.models.Device;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.DeviceData;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechCommand;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;


/**
 * Created by ksg on 6/17/16
 */
public class RoomConditionsHandler extends BaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomConditionsHandler.class);

    private static final String DEFAULT_SENSOR_UNIT = "f";
    private static final Float NO_SOUND_FILL_VALUE_DB = (float) 35; // Replace with this value when Sense isn't capturing audio


    private final SpeechCommandDAO speechCommandDAO;
    private final DeviceDataDAODynamoDB deviceDataDAODynamoDB;
    private final DeviceDAO deviceDAO;
    private final SenseColorDAO senseColorDAO;
    private final CalibrationDAO calibrationDAO;

    public RoomConditionsHandler(final SpeechCommandDAO speechCommandDAO,
                                 final DeviceDataDAODynamoDB deviceDataDAODynamoDB,
                                 final DeviceDAO deviceDAO,
                                 final SenseColorDAO senseColorDAO,
                                 final CalibrationDAO calibrationDAO) {
        super("room_condition", speechCommandDAO, getAvailableActions());
        this.speechCommandDAO = speechCommandDAO;
        this.deviceDataDAODynamoDB = deviceDataDAODynamoDB;
        this.deviceDAO = deviceDAO;
        this.senseColorDAO = senseColorDAO;
        this.calibrationDAO = calibrationDAO;
    }

    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("the temperature", SpeechCommand.ROOM_TEMPERATURE);
        tempMap.put("the humidity", SpeechCommand.ROOM_HUMIDITY);
        tempMap.put("light level", SpeechCommand.ROOM_LIGHT);
        tempMap.put("how bright", SpeechCommand.ROOM_LIGHT);
        tempMap.put("sound level", SpeechCommand.ROOM_SOUND);
        tempMap.put("how noisy", SpeechCommand.ROOM_SOUND);
        return tempMap;
    }


    @Override
    public HandlerResult executionCommand(final String text, final String senseId, final Long accountId) {
        final Optional<SpeechCommand> optionalCommand = getCommand(text); // TODO: ensure that only valid commands are returned
        final Map<String, String> response = Maps.newHashMap();

        if (optionalCommand.isPresent()) {
            // TODO: get units preference
            response.putAll(getCurrentRoomConditions(accountId, optionalCommand.get(), DEFAULT_SENSOR_UNIT));
        }

        return new HandlerResult(HandlerType.ROOM_CONDITIONS, response);
    }


    private Map<String,String> getCurrentRoomConditions(final Long accountId, final SpeechCommand command, final String unit) {
        final Map<String, String> response = Maps.newHashMap();

        final Optional<DeviceAccountPair> optionalDeviceAccountPair = deviceDAO.getMostRecentSensePairByAccountId(accountId);
        if (!optionalDeviceAccountPair.isPresent()) {
            response.put("result", HandlerResult.Outcome.FAIL.getValue());
            response.put("error", "no paired sense");
            return response;
        }

        final String senseId = optionalDeviceAccountPair.get().externalDeviceId;

        // TODO: check sensor view available? Or just return no data response

        // look back last 30 minutes
        Integer thresholdInMinutes = 15;
        Integer mostRecentLookBackMinutes = 30;
        final DateTime maxDT = DateTime.now(DateTimeZone.UTC).plusMinutes(2);
        final DateTime minDT = DateTime.now(DateTimeZone.UTC).minusMinutes(mostRecentLookBackMinutes);
        final Optional<DeviceData> optionalData = deviceDataDAODynamoDB.getMostRecent(accountId, senseId, maxDT, minDT);

        if (!optionalData.isPresent()) {
            response.put("result", HandlerResult.Outcome.FAIL.getValue());
            response.put("error", "no data");
            return response;
        }

        final Optional<Device.Color> color = senseColorDAO.getColorForSense(senseId);
        final DeviceData deviceData = optionalData.get().withCalibratedLight(color); // with light calibration
        final Optional<Calibration> calibrationOptional = calibrationDAO.getStrict(senseId);

        final CurrentRoomState roomState = CurrentRoomState.fromDeviceData(deviceData, DateTime.now(), thresholdInMinutes, unit, calibrationOptional, NO_SOUND_FILL_VALUE_DB);
        if (roomState.temperature.condition.equals(CurrentRoomState.State.Condition.UNKNOWN)) {
            response.put("result", HandlerResult.Outcome.FAIL.getValue());
            response.put("error", "data too old");
            return response;
        }

        final String sensorValue;
        final String sensorUnit;
        final String sensorName;
        switch (command) {
            case ROOM_TEMPERATURE:
                sensorName = "temperature";
                if (unit.equalsIgnoreCase("f")) {
                    sensorUnit = "°F";
                    sensorValue = String.valueOf(celsiusToFahrenheit(roomState.temperature.value));

                } else {
                    sensorUnit = "°C";
                    sensorValue = String.valueOf(Math.round(roomState.temperature.value));
                }
                break;
            case ROOM_HUMIDITY:
                sensorName = "humidity";
                sensorValue = String.valueOf(Math.round(roomState.humidity.value));
                sensorUnit = "%";
                break;
            case ROOM_LIGHT:
                sensorName = "light";
                sensorValue = String.valueOf(Math.round(roomState.light.value));
                sensorUnit = "lux";
                break;
            case ROOM_SOUND:
                sensorName = "sound";
                sensorValue = String.valueOf(Math.round(roomState.sound.value));
                sensorUnit = "decibels";
                break;
            default:
                sensorName = "";
                sensorValue = "";
                sensorUnit = "";
                break;
        }

        LOGGER.debug("action=get-room-condition command={} value={}", command.toString(), sensorValue);

        if (sensorName.isEmpty()) {
            response.put("result", HandlerResult.Outcome.FAIL.getValue());
            response.put("error", "invalid sensor");
        } else {
            response.put("result", HandlerResult.Outcome.OK.getValue());
            response.put("sensor", sensorName);
            response.put("value", sensorValue);
            response.put("unit", sensorUnit);
        }

        return response;
    }

    private static int celsiusToFahrenheit(final double value) {
        return (int) Math.round((value * 9.0) / 5.0) + 32;
    }

}