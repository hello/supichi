package is.hello.speech.resources.v1;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableList;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.speech.SpeechResult;
import com.hello.suripu.core.util.HelloHttpHeader;
import is.hello.speech.clients.SpeechClient;
import is.hello.speech.core.api.Response;
import is.hello.speech.core.handlers.executors.HandlerExecutor;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechServiceResult;
import is.hello.speech.core.models.TextQuery;
import is.hello.speech.kinesis.SpeechKinesisProducer;
import is.hello.speech.core.models.UploadResponseParam;
import is.hello.speech.utils.ResponseBuilder;
import is.hello.speech.utils.WatsonResponseBuilder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.io.IOException;
import java.util.UUID;


@Path("/v1/upload")
@Produces(MediaType.APPLICATION_JSON)
public class UploadResource {

    private final static Logger LOGGER = LoggerFactory.getLogger(UploadResource.class);

    private final SpeechClient speechClient;
    private final SignedBodyHandler signedBodyHandler;
    private final HandlerExecutor handlerExecutor;

    private final DeviceDAO deviceDAO;

    private final SpeechKinesisProducer speechKinesisProducer;

    private final ResponseBuilder responseBuilder;
    private final WatsonResponseBuilder watsonResponseBuilder;

    @Context
    HttpServletRequest request;

    public UploadResource(final SpeechClient speechClient,
                          final SignedBodyHandler signedBodyHandler,
                          final HandlerExecutor handlerExecutor,
                          final DeviceDAO deviceDAO,
                          final SpeechKinesisProducer speechKinesisProducer,
                          final ResponseBuilder responseBuilder,
                          final WatsonResponseBuilder watsonResponseBuilder
    ) {
        this.speechClient = speechClient;
        this.signedBodyHandler = signedBodyHandler;
        this.handlerExecutor = handlerExecutor;
        this.deviceDAO = deviceDAO;
        this.speechKinesisProducer = speechKinesisProducer;
        this.responseBuilder = responseBuilder;
        this.watsonResponseBuilder = watsonResponseBuilder;
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
            return responseBuilder.response(Response.SpeechResponse.Result.REJECTED, includeProtobuf, executeResult, responseParam);
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
        final SpeechResult speechResult = new SpeechResult.Builder()
                .withAccountId(accountId)
                .withSenseId(senseId)
                .withAudioIndentifier(audioUUID)
                .withDateTimeUTC(DateTime.now(DateTimeZone.UTC))
                .build();
        speechKinesisProducer.addResult(speechResult, body);

        // process audio
        try {
            final SpeechServiceResult resp = speechClient.stream(body, sampling);

            // try to execute command in transcript
            if (resp.getTranscript().isPresent()) {

                executeResult = handlerExecutor.handle(senseId, accountId, resp.getTranscript().get());
            }

            if (executeResult.handlerType.equals(HandlerType.WEATHER)) {
                return watsonResponseBuilder.response(executeResult, responseParam);
            }

            // TODO: response-builder
            if (!executeResult.handlerType.equals(HandlerType.NONE)) {
                return responseBuilder.response(Response.SpeechResponse.Result.OK, includeProtobuf, executeResult, responseParam);
            } else {
                return responseBuilder.response(Response.SpeechResponse.Result.TRY_AGAIN, includeProtobuf, executeResult, responseParam);
            }
        } catch (Exception e) {
            LOGGER.error("action=streaming error={}", e.getMessage());
        }

        return responseBuilder.response(Response.SpeechResponse.Result.REJECTED, includeProtobuf, executeResult, responseParam);
    }

    @Path("/text")
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public byte[] text(@Valid final TextQuery query,
                       @DefaultValue("adpcm") @QueryParam("response") final UploadResponseParam responseParam
    ) throws InterruptedException, IOException {

        final ImmutableList<DeviceAccountPair> accounts = deviceDAO.getAccountIdsForDeviceId(query.senseId);

        LOGGER.debug("info=sense-id id={}", query.senseId);
        if (accounts.isEmpty()) {
            LOGGER.error("error=no-paired-sense-found sense_id={}", query.senseId);
            return responseBuilder.response(Response.SpeechResponse.Result.REJECTED, false, HandlerResult.emptyResult(), responseParam);
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

            if (executeResult.handlerType.equals(HandlerType.WEATHER)) {
                return watsonResponseBuilder.response(executeResult, responseParam);
            }

            // TODO: response-builder
            if (!executeResult.handlerType.equals(HandlerType.NONE)) {
                return responseBuilder.response(Response.SpeechResponse.Result.OK, false, executeResult, responseParam);
            }
            return responseBuilder.response(Response.SpeechResponse.Result.TRY_AGAIN, false, executeResult, responseParam);
        } catch (Exception e) {
            LOGGER.error("action=streaming error={}", e.getMessage());
        }

        return responseBuilder.response(Response.SpeechResponse.Result.REJECTED, false, HandlerResult.emptyResult(), responseParam);
    }
}
