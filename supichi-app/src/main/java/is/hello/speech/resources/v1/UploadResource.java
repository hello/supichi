package is.hello.speech.resources.v1;

import com.google.common.collect.ImmutableList;

import com.codahale.metrics.annotation.Timed;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.speech.models.Result;
import com.hello.suripu.core.speech.models.SpeechResult;
import com.hello.suripu.core.speech.models.SpeechToTextService;
import com.hello.suripu.core.speech.models.WakeWord;
import com.hello.suripu.core.util.HelloHttpHeader;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import is.hello.speech.clients.SpeechClient;
import is.hello.speech.core.api.Response;
import is.hello.speech.core.api.SpeechResultsKinesis;
import is.hello.speech.core.handlers.executors.HandlerExecutor;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechServiceResult;
import is.hello.speech.core.models.TextQuery;
import is.hello.speech.core.models.UploadResponseParam;
import is.hello.speech.core.models.responsebuilder.DefaultResponseBuilder;
import is.hello.speech.core.response.SupichiResponseBuilder;
import is.hello.speech.core.response.SupichiResponseType;
import is.hello.speech.kinesis.SpeechKinesisProducer;


@Path("/v1/upload")
@Produces(MediaType.APPLICATION_JSON)
public class UploadResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(UploadResource.class);

    private final static byte[] EMPTY_BYTE = new byte[0];

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
                            @DefaultValue("8000") @QueryParam("r") final Integer sampling,
                            @DefaultValue("false") @QueryParam("pb") final boolean includeProtobuf,
                            @DefaultValue("adpcm") @QueryParam("response") final UploadResponseParam responseParam
    ) throws InterruptedException, IOException {

        LOGGER.debug("action=received-bytes size={}", signedBody.length);

        final String senseId = this.request.getHeader(HelloHttpHeader.SENSE_ID);
        if(senseId == null) {
            LOGGER.error("error=missing-sense-id-header");
            throw new WebApplicationException(javax.ws.rs.core.Response.Status.BAD_REQUEST);
        }

        // old default: 8AF6441AF72321F4  C8DAAC353AEFA4A9
        final byte[] body = signedBodyHandler.extractAudio(senseId, signedBody);

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

        // save audio to Kinesis, and save speech-timeline
        final String audioUUID = UUID.randomUUID().toString();
        final DateTime speechCreated = DateTime.now(DateTimeZone.UTC);
        SpeechResult.Builder builder = new SpeechResult.Builder();
        builder.withAccountId(accountId)
                .withSenseId(senseId)
                .withAudioIndentifier(audioUUID)
                .withDateTimeUTC(speechCreated);
        speechKinesisProducer.addResult(builder.build(), SpeechResultsKinesis.SpeechResultsData.Action.TIMELINE, body);

        builder.withUpdatedUTC(DateTime.now(DateTimeZone.UTC))
                .withWakeWord(WakeWord.OK_SENSE)
                .withService(SpeechToTextService.GOOGLE);


        // process audio
        try {
            final SpeechServiceResult resp = speechClient.stream(body, sampling);

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

    @Path("/text")
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public byte[] text(@Valid final TextQuery query,
                       @DefaultValue("adpcm") @QueryParam("response") final UploadResponseParam responseParam
    ) throws InterruptedException, IOException {

        final boolean includeProtobuf = false;
        final ImmutableList<DeviceAccountPair> accounts = deviceDAO.getAccountIdsForDeviceId(query.senseId);

        LOGGER.debug("info=sense-id id={}", query.senseId);
        if (accounts.isEmpty()) {
            LOGGER.error("error=no-paired-sense-found sense_id={}", query.senseId);
            return responseBuilders.get(SupichiResponseType.S3).response(Response.SpeechResponse.Result.REJECTED, false, HandlerResult.emptyResult(), responseParam);
        }

        // TODO: for now, pick the smallest account-id as the primary id
        Long accountId = accounts.get(0).accountId;
        for (final DeviceAccountPair accountPair : accounts) {
            if (accountPair.accountId < accountId) {
                accountId = accountPair.accountId;
            }
        }

        LOGGER.debug("action=execute-handler sense_id={} account_id={}", query.senseId, accountId);
        try {

            final HandlerResult executeResult = handlerExecutor.handle(query.senseId, accountId, query.transcript);

            final SupichiResponseType responseType = handlerMap.getOrDefault(executeResult.handlerType, SupichiResponseType.WATSON);
            final SupichiResponseBuilder responseBuilder = responseBuilders.get(responseType);

            // TODO: response-builder
            if (!executeResult.handlerType.equals(HandlerType.NONE)) {
                return responseBuilder.response(Response.SpeechResponse.Result.OK, includeProtobuf, executeResult, responseParam);
            }

            return responseBuilder.response(Response.SpeechResponse.Result.TRY_AGAIN, includeProtobuf, executeResult, responseParam);
        } catch (Exception e) {
            LOGGER.error("action=streaming error={}", e.getMessage());
        }

        return responseBuilders.get(SupichiResponseType.S3).response(Response.SpeechResponse.Result.REJECTED, false, HandlerResult.emptyResult(), responseParam);
    }
}
