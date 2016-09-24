package is.hello.speech.resources.v2;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.speech.models.Result;
import com.hello.suripu.core.speech.models.SpeechResult;
import com.hello.suripu.core.speech.models.SpeechToTextService;
import com.hello.suripu.core.speech.models.WakeWord;
import com.hello.suripu.core.util.HelloHttpHeader;
import is.hello.speech.clients.SpeechClient;
import is.hello.speech.core.api.Response;
import is.hello.speech.core.api.Speech;
import is.hello.speech.core.api.SpeechResultsKinesis;
import is.hello.speech.core.handlers.executors.HandlerExecutor;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechServiceResult;
import is.hello.speech.core.models.UploadResponseParam;
import is.hello.speech.core.models.responsebuilder.DefaultResponseBuilder;
import is.hello.speech.core.response.SupichiResponseBuilder;
import is.hello.speech.core.response.SupichiResponseType;
import is.hello.speech.core.text2speech.AudioUtils;
import is.hello.speech.kinesis.SpeechKinesisProducer;
import is.hello.speech.resources.v1.InvalidSignatureException;
import is.hello.speech.resources.v1.InvalidSignedBodyException;
import is.hello.speech.resources.v1.SignedBodyHandler;
import is.hello.speech.resources.v1.UploadData;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.open.audio.AudioException;
import uk.ac.open.audio.adpcm.ADPCMDecoder;
import uk.ac.open.audio.adpcm.ADPCMEncoder;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;


@Path("/v2/upload")
@Produces(MediaType.APPLICATION_JSON)
public class UploadResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(UploadResource.class);
    private static final byte[] EMPTY_BYTE = new byte[0];
    private static final int ADPCM_BYTES_TO_DROP = 12000;

    private static final int ADPCM_STATE_SIZE = 3;

    private final SpeechClient speechClient;
    private final SignedBodyHandler signedBodyHandler;
    private final HandlerExecutor handlerExecutor;

    private final DeviceDAO deviceDAO;

    private final SpeechKinesisProducer speechKinesisProducer;


    private final Map<SupichiResponseType, SupichiResponseBuilder> responseBuilders;
    private final Map<HandlerType, SupichiResponseType> handlerMap;

    @Context
    HttpServletRequest request;

    public UploadResource(final SpeechClient speechClient,
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

    @Path("/audio")
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public byte[] streaming(final byte[] signedBody,
                            @DefaultValue("16000") @QueryParam("r") final Integer sampling,
                            @DefaultValue("false") @QueryParam("pb") final boolean includeProtobuf,
                            @DefaultValue("mp3") @QueryParam("response") final UploadResponseParam responseParam
    ) throws InterruptedException, IOException, AudioException {

        LOGGER.debug("action=received-bytes size={}", signedBody.length);

        final String senseId = this.request.getHeader(HelloHttpHeader.SENSE_ID);
        if(senseId == null) {
            LOGGER.error("error=missing-sense-id-header");
            throw new WebApplicationException(javax.ws.rs.core.Response.Status.BAD_REQUEST);
        }

        // parse audio and protobuf
        final UploadData uploadData;
        try {
            uploadData = signedBodyHandler.extractUploadData(senseId, signedBody);
        } catch (InvalidSignedBodyException e) {
            throw new WebApplicationException(javax.ws.rs.core.Response.Status.BAD_REQUEST);
        } catch(InvalidSignatureException e) {
            throw new WebApplicationException(javax.ws.rs.core.Response.Status.UNAUTHORIZED);
        }

        LOGGER.debug("action=get-pb-values word={} confidence={}", uploadData.speechData.getWord(), uploadData.speechData.getConfidence());

        final byte[] body = uploadData.audioBody;

        if(body.length == 0) {
            throw new WebApplicationException(javax.ws.rs.core.Response.Status.BAD_REQUEST);
        }

        final ImmutableList<DeviceAccountPair> accounts = deviceDAO.getAccountIdsForDeviceId(senseId);
        LOGGER.debug("info=sense-id id={}", senseId);
        HandlerResult executeResult = HandlerResult.emptyResult();

        if (accounts.isEmpty()) {
            LOGGER.error("error=no-paired-sense-found sense_id={}", senseId);
            return responseBuilders.get(SupichiResponseType.S3).response(Response.SpeechResponse.Result.REJECTED, includeProtobuf, executeResult, responseParam);
        }

        // TODO: for now, pick the smallest account-id as the primary id
        Long accountId = accounts.get(0).accountId;
        for (final DeviceAccountPair accountPair : accounts) {
            if (accountPair.accountId < accountId) {
                accountId = accountPair.accountId;
            }
        }

        LOGGER.debug("action=get-speech-audio sense_id={} account_id={} response_type={}", senseId, accountId, responseParam.type().name());

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
        final Speech.keyword keyword = uploadData.speechData.getWord();
        final WakeWord wakeWord = WakeWord.fromString(keyword.name());
        final Map<String, Float> wakeWordConfidence = setWakeWordConfidence(wakeWord, (float) uploadData.speechData.getConfidence());
        builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                .withWakeWord(wakeWord)
                .withWakeWordsConfidence(wakeWordConfidence)
                .withService(SpeechToTextService.GOOGLE);


        if (keyword.equals(Speech.keyword.STOP) || keyword.equals(Speech.keyword.SNOOZE)) {
            LOGGER.debug("action=encounter-STOP-SNOOZE keyword={}", keyword);
            builder.withResult(Result.OK);
            speechKinesisProducer.addResult(builder.build(), SpeechResultsKinesis.SpeechResultsData.Action.PUT_ITEM, EMPTY_BYTE);

            return EMPTY_BYTE;
        }

        // convert audio: ADPCM to 16-bit 16k PCM
        final byte[] decoded;
        if (true) {
             decoded = AudioUtils.decodeADPShitMAudio(body);
        } else {
            final int chunkSize = ADPCMEncoder.BLOCKBYTES - ADPCM_STATE_SIZE;
            final int chunks = body.length / chunkSize;

            // for reading body bytes
            final ByteArrayInputStream inputStream = new ByteArrayInputStream(body);
            byte[] dataBuffer = new byte[chunkSize];

            // first 3 bytes to decoder contains previous chunk values and last stepIndex
            byte[] startBuffer = new byte[]{0, 0, 0};

            final ByteArrayOutputStream toDecodeStream = new ByteArrayOutputStream(); // intermediate stream
            final ByteArrayOutputStream decodedStream = new ByteArrayOutputStream();

            for (int i = 0; i < chunks + 1; i++) {

                final int readSize = inputStream.read(dataBuffer, 0, chunkSize);
                if (readSize != chunkSize) {
                    LOGGER.debug("action=input-stream-read chunk={} expect={} read={}", i, chunkSize, readSize);
                    break;
                }

                toDecodeStream.write(startBuffer); // add previous state
                toDecodeStream.write(dataBuffer);

                final ADPCMDecoder.DecodeResult decodeResult = ADPCMDecoder.decodeBlock(toDecodeStream.toByteArray(), 0);
                toDecodeStream.reset();

                // fill in previous values
                final int outputSize = decodeResult.data.length;
                startBuffer[0] = decodeResult.data[outputSize - 2];
                startBuffer[1] = decodeResult.data[outputSize - 1];
                startBuffer[2] = (byte) decodeResult.stepIndex;

                decodedStream.write(decodeResult.data);
            }

            // process audio
            decoded = decodedStream.toByteArray();
        }
        LOGGER.debug("action=convert-adpcm-pcm input_size={} output_size={}", body.length, decoded.length);

        try {
            final SpeechServiceResult resp = speechClient.stream(decoded, sampling);

            // try to execute command in transcript
            if (resp.getTranscript().isPresent()) {

                // save transcript results to Kinesis
                final String transcribedText = resp.getTranscript().get();
                builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                        .withConfidence(resp.getConfidence())
                        .withText(transcribedText);

                // try to execute text command
                executeResult = handlerExecutor.handle(senseId, accountId, transcribedText);
            }

            final SupichiResponseType responseType = handlerMap.getOrDefault(executeResult.handlerType, SupichiResponseType.S3);
            final SupichiResponseBuilder responseBuilder = responseBuilders.get(responseType);

            // TODO: response-builder
            if (!executeResult.handlerType.equals(HandlerType.NONE)) {
                // save OK speech result
                Result commandResult = Result.OK;
                if (executeResult.responseParameters.containsKey("result")) {
                    commandResult = executeResult.responseParameters.get("result").equals(HandlerResult.Outcome.OK.getValue()) ? Result.OK : Result.REJECTED;
                }
                builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                        .withCommand(executeResult.command)
                        .withHandlerType(executeResult.handlerType.value)
                        .withResponseText(executeResult.getResponseText())
                        .withResult(commandResult);
                speechKinesisProducer.addResult(builder.build(), SpeechResultsKinesis.SpeechResultsData.Action.PUT_ITEM, EMPTY_BYTE);

                return responseBuilder.response(Response.SpeechResponse.Result.OK, includeProtobuf, executeResult, responseParam);
            }

            // save TRY_AGAIN speech result
            builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                    .withResponseText(DefaultResponseBuilder.DEFAULT_TEXT.get(Response.SpeechResponse.Result.TRY_AGAIN))
                    .withResult(Result.TRY_AGAIN);
            speechKinesisProducer.addResult(builder.build(), SpeechResultsKinesis.SpeechResultsData.Action.PUT_ITEM, EMPTY_BYTE);

            return responseBuilder.response(Response.SpeechResponse.Result.TRY_AGAIN, includeProtobuf, executeResult, responseParam);
        } catch (Exception e) {
            LOGGER.error("action=streaming error={}", e.getMessage());
        }

        // no text or command found, save REJECT result
        builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                .withResponseText(DefaultResponseBuilder.DEFAULT_TEXT.get(Response.SpeechResponse.Result.REJECTED))
                .withResult(Result.REJECTED);
        speechKinesisProducer.addResult(builder.build(), SpeechResultsKinesis.SpeechResultsData.Action.PUT_ITEM, EMPTY_BYTE);

        return responseBuilders.get(SupichiResponseType.S3).response(Response.SpeechResponse.Result.REJECTED, includeProtobuf, executeResult, responseParam);
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
