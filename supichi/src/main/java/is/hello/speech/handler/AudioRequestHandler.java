package is.hello.speech.handler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.speech.models.Result;
import com.hello.suripu.core.speech.models.SpeechResult;
import com.hello.suripu.core.speech.models.SpeechToTextService;
import com.hello.suripu.core.speech.models.WakeWord;
import is.hello.speech.clients.SpeechClient;
import is.hello.speech.core.api.Response;
import is.hello.speech.core.api.Speech;
import is.hello.speech.core.api.SpeechResultsKinesis;
import is.hello.speech.core.handlers.executors.HandlerExecutor;
import is.hello.speech.core.handlers.results.Outcome;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechServiceResult;
import is.hello.speech.core.models.responsebuilder.DefaultResponseBuilder;
import is.hello.speech.core.response.SupichiResponseBuilder;
import is.hello.speech.core.response.SupichiResponseType;
import is.hello.speech.core.text2speech.AudioUtils;
import is.hello.speech.kinesis.SpeechKinesisProducer;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

public class AudioRequestHandler {

    private static Logger LOGGER = LoggerFactory.getLogger(AudioRequestHandler.class);
    private final SpeechClient speechClient;
    private final SignedBodyHandler signedBodyHandler;
    private final HandlerExecutor handlerExecutor;

    private final DeviceDAO deviceDAO;

    private final SpeechKinesisProducer speechKinesisProducer;
    private final Map<SupichiResponseType, SupichiResponseBuilder> responseBuilders;
    private final Map<HandlerType, SupichiResponseType> handlerMap;

    private static final byte[] EMPTY_BYTE = new byte[0];


    public AudioRequestHandler(final SpeechClient speechClient,
                               final SignedBodyHandler signedBodyHandler,
                               final HandlerExecutor handlerExecutor,
                               final DeviceDAO deviceDAO,
                               final SpeechKinesisProducer speechKinesisProducer,
                               final Map<SupichiResponseType, SupichiResponseBuilder> responseBuilders,
                               final Map<HandlerType, SupichiResponseType> handlerMap) {
        this.speechClient = speechClient;
        this.signedBodyHandler = signedBodyHandler;
        this.handlerExecutor = handlerExecutor;
        this.deviceDAO = deviceDAO;
        this.speechKinesisProducer = speechKinesisProducer;
        this.responseBuilders = responseBuilders;
        this.handlerMap = handlerMap;
    }

    public WrappedResponse handle(final byte[] signedBody, final String senseId) {
        LOGGER.debug("action=received-bytes size={}", signedBody.length);

        // parse audio and protobuf
        final UploadData uploadData;
        try {
            uploadData = signedBodyHandler.extractUploadData(senseId, signedBody);
        } catch (InvalidSignedBodyException e) {
            LOGGER.error("error=invalid-signed-body sense_id={} msg={}", senseId, e.getMessage());
            return WrappedResponse.error(RequestError.INVALID_BODY);
        } catch(InvalidSignatureException e) {
            LOGGER.error("error=invalid-signature sense_id={} msg={}", senseId, e.getMessage());
            return WrappedResponse.error(RequestError.INVALID_SIGNATURE);
        }

        LOGGER.debug("action=get-pb-values word={} confidence={}", uploadData.request.getWord(), uploadData.request.getConfidence());
        final byte[] body = uploadData.audioBody;

        if(body.length == 0) {
            return WrappedResponse.error(RequestError.EMPTY_BODY);
        }

        final ImmutableList<DeviceAccountPair> accounts = deviceDAO.getAccountIdsForDeviceId(senseId);
        LOGGER.debug("info=sense-id id={}", senseId);
        HandlerResult executeResult = HandlerResult.emptyResult();

        if (accounts.isEmpty()) {
            LOGGER.error("error=no-paired-sense-found sense_id={}", senseId);
            final byte[] content = responseBuilders.get(SupichiResponseType.S3).response(Response.SpeechResponse.Result.REJECTED, executeResult, uploadData.request);
            return WrappedResponse.ok(content);
        }

        // TODO: for now, pick the smallest account-id as the primary id
        Long accountId = accounts.get(0).accountId;
        for (final DeviceAccountPair accountPair : accounts) {
            if (accountPair.accountId < accountId) {
                accountId = accountPair.accountId;
            }
        }

        LOGGER.debug("action=get-speech-audio sense_id={} account_id={} response_type={}", senseId, accountId, uploadData.request.getResponse());

        // save audio to Kinesis
        final String audioUUID = UUID.randomUUID().toString();
        final DateTime speechCreated = DateTime.now(DateTimeZone.UTC);

        SpeechResult.Builder builder = new SpeechResult.Builder();
        builder.withAccountId(accountId)
                .withSenseId(senseId)
                .withAudioIndentifier(audioUUID)
                .withDateTimeUTC(speechCreated);
        speechKinesisProducer.addResult(builder.build(), SpeechResultsKinesis.SpeechResultsData.Action.TIMELINE, body);

        // return empty bytes for certain wakeword
        final Speech.Keyword keyword = uploadData.request.getWord();
        final WakeWord wakeWord = WakeWord.fromString(keyword.name());
        final Map<String, Float> wakeWordConfidence = setWakeWordConfidence(wakeWord, (float) uploadData.request.getConfidence());
        builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                .withWakeWord(wakeWord)
                .withWakeWordsConfidence(wakeWordConfidence)
                .withService(SpeechToTextService.GOOGLE);


        if (keyword.equals(Speech.Keyword.STOP) || keyword.equals(Speech.Keyword.SNOOZE)) {
            LOGGER.debug("action=encounter-STOP-SNOOZE keyword={}", keyword);
            builder.withResult(Result.OK);
            speechKinesisProducer.addResult(builder.build(), SpeechResultsKinesis.SpeechResultsData.Action.PUT_ITEM, EMPTY_BYTE);

            return WrappedResponse.empty();
        }

        try {
            // convert audio: ADPCM to 16-bit 16k PCM
            final byte[] decoded = AudioUtils.decodeADPShitMAudio(body);
            LOGGER.debug("action=convert-adpcm-pcm input_size={} output_size={}", body.length, decoded.length);
            final SpeechServiceResult resp = speechClient.stream(decoded, uploadData.request.getSamplingRate());


            if (!resp.getTranscript().isPresent()) {
                LOGGER.warn("action=google-transcript-failed sense_id={}", senseId);
                return WrappedResponse.empty();
            }

            // save transcript results to Kinesis
            final String transcribedText = resp.getTranscript().get();
            builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                    .withConfidence(resp.getConfidence())
                    .withText(transcribedText);

            // try to execute text command
            executeResult = handlerExecutor.handle(senseId, accountId, transcribedText);

            final SupichiResponseType responseType = handlerMap.getOrDefault(executeResult.handlerType, SupichiResponseType.S3);
            final SupichiResponseBuilder responseBuilder = responseBuilders.get(responseType);

            // TODO: response-builder
            if (!executeResult.handlerType.equals(HandlerType.NONE)) {
                // save OK speech result
                Result commandResult = Result.OK;
                if (executeResult.responseParameters.containsKey("result")) {
                    commandResult = executeResult.responseParameters.get("result").equals(Outcome.OK.getValue()) ? Result.OK : Result.REJECTED;
                }
                builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                        .withCommand(executeResult.command)
                        .withHandlerType(executeResult.handlerType.value)
                        .withResponseText(executeResult.getResponseText())
                        .withResult(commandResult);
                speechKinesisProducer.addResult(builder.build(), SpeechResultsKinesis.SpeechResultsData.Action.PUT_ITEM, EMPTY_BYTE);

                final byte[] content = responseBuilder.response(Response.SpeechResponse.Result.OK, executeResult, uploadData.request);
                return WrappedResponse.ok(content);
            }

            // save TRY_AGAIN speech result
            builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                    .withResponseText(DefaultResponseBuilder.DEFAULT_TEXT.get(Response.SpeechResponse.Result.TRY_AGAIN))
                    .withResult(Result.TRY_AGAIN);
            speechKinesisProducer.addResult(builder.build(), SpeechResultsKinesis.SpeechResultsData.Action.PUT_ITEM, EMPTY_BYTE);

            final byte[] content = responseBuilder.response(Response.SpeechResponse.Result.TRY_AGAIN, executeResult, uploadData.request);
            return WrappedResponse.ok(content);
        } catch (Exception e) {
            LOGGER.error("action=streaming error={}", e.getMessage());
        }

        // no text or command found, save REJECT result
        builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                .withResponseText(DefaultResponseBuilder.DEFAULT_TEXT.get(Response.SpeechResponse.Result.REJECTED))
                .withResult(Result.REJECTED);
        speechKinesisProducer.addResult(builder.build(), SpeechResultsKinesis.SpeechResultsData.Action.PUT_ITEM, EMPTY_BYTE);

        final byte[] content = responseBuilders.get(SupichiResponseType.S3).response(Response.SpeechResponse.Result.REJECTED, executeResult, uploadData.request);
        return WrappedResponse.ok(content);
    }

    private Map<String, Float> setWakeWordConfidence(final WakeWord wakeWord, final Float confidence) {
        final Map<String, Float> wakeWordConfidence = Maps.newHashMap();
        for (final WakeWord word : WakeWord.values()) {
            if (word.equals(WakeWord.NULL)) {
                continue;
            }
            if (word.equals(wakeWord)) {
                wakeWordConfidence.put(wakeWord.getWakeWordText(), confidence);
            } else {
                wakeWordConfidence.put(word.getWakeWordText(), 0.0f);
            }
        }
        return wakeWordConfidence;
    }
}
