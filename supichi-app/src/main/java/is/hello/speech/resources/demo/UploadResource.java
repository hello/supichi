package is.hello.speech.resources.demo;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.util.Md5Utils;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableList;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.speech.models.SpeechResult;
import com.hello.suripu.core.util.HelloHttpHeader;
import is.hello.speech.clients.SpeechClient;
import is.hello.speech.core.api.Response;
import is.hello.speech.core.api.SpeechResultsKinesis;
import is.hello.speech.core.handlers.executors.HandlerExecutor;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechServiceResult;
import is.hello.speech.core.models.TextQuery;
import is.hello.speech.kinesis.SpeechKinesisProducer;
import is.hello.speech.core.models.UploadResponseParam;
import is.hello.speech.utils.S3ResponseBuilder;
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
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;


@Path("/upload")
@Produces(MediaType.APPLICATION_JSON)
public class UploadResource {

    private final static Logger LOGGER = LoggerFactory.getLogger(UploadResource.class);

    private final AmazonS3 s3;
    private final String bucketName;
    private final SpeechClient speechClient;
    private final HandlerExecutor handlerExecutor;

    private final DeviceDAO deviceDAO;
    private final SpeechKinesisProducer speechKinesisProducer;

    private final S3ResponseBuilder s3ResponseBuilder;
    private final WatsonResponseBuilder watsonResponseBuilder;

    @Context
    HttpServletRequest request;

    public UploadResource(final AmazonS3 s3, final String bucketName,
                          final SpeechClient speechClient,
                          final HandlerExecutor handlerExecutor,
                          final DeviceDAO deviceDAO,
                          final SpeechKinesisProducer speechKinesisProducer,
                          final S3ResponseBuilder s3ResponseBuilder,
                          final WatsonResponseBuilder watsonResponseBuilder) {
        this.s3 = s3;
        this.bucketName = bucketName;
        this.speechClient = speechClient;
        this.handlerExecutor = handlerExecutor;
        this.deviceDAO = deviceDAO;
        this.s3ResponseBuilder = s3ResponseBuilder;
        this.watsonResponseBuilder = watsonResponseBuilder;
        this.speechKinesisProducer = speechKinesisProducer;
    }

    @Path("{prefix}")
    @POST
    @Timed
    public String sayHello(final @PathParam("prefix") String prefix, byte[] body) throws InterruptedException {
        final DateTime now = DateTime.now(DateTimeZone.forID("America/Los_Angeles"));

        final String key = String.format("%s/%s", prefix, now.toString().replace(" ", "_"));

        try (final ByteArrayInputStream bas = new ByteArrayInputStream(body)) {

            final String md5 = Md5Utils.md5AsBase64(body);
            final ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentMD5(md5);
            metadata.setContentLength(body.length);
            LOGGER.info("key={} md5={}", key, md5);
            s3.putObject(bucketName, key, bas, metadata);

            return "OK";
        } catch (IOException exception) {
            LOGGER.error("{}", exception.getMessage());
        }
        return "KO";
    }


    @Path("/audio")
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public byte[] streaming(final byte[] body,
                            @DefaultValue("8000") @QueryParam("r") final Integer sampling,
                            @DefaultValue("false") @QueryParam("pb") final boolean includeProtobuf,
                            @DefaultValue("adpcm") @QueryParam("response") final UploadResponseParam responseParam
    ) throws InterruptedException, IOException {

        LOGGER.debug("action=received-bytes size={}", body.length);

        HandlerResult executeResult = HandlerResult.emptyResult();

        final String senseId = this.request.getHeader(HelloHttpHeader.SENSE_ID);
        // old default: 8AF6441AF72321F4  C8DAAC353AEFA4A9

        if(senseId == null) {
            LOGGER.error("error=missing-sense-id-header");
            throw new WebApplicationException(javax.ws.rs.core.Response.Status.BAD_REQUEST);
        }
        // demo 8D6C0F005D469DE7

        final ImmutableList<DeviceAccountPair> accounts = deviceDAO.getAccountIdsForDeviceId(senseId);

        LOGGER.debug("info=sense-id id={}", senseId);

        if (accounts.isEmpty()) {
            LOGGER.error("error=no-paired-sense-found sense_id={}", senseId);
            return s3ResponseBuilder.response(Response.SpeechResponse.Result.REJECTED, includeProtobuf, executeResult, responseParam);
        }

        // TODO: for now, pick the smallest account-id as the primary id
        Long accountId = accounts.get(0).accountId;
        for (final DeviceAccountPair accountPair : accounts) {
            if (accountPair.accountId < accountId) {
                accountId = accountPair.accountId;
            }
        }

        LOGGER.debug("action=get-speech-audio sense_id={} account_id={}", senseId, accountId);

        // save audio to Kinesis
        final String audioUUID = UUID.randomUUID().toString();
        final DateTime speechCreated = DateTime.now(DateTimeZone.UTC);
        final SpeechResult speechResult = new SpeechResult.Builder()
                .withAccountId(accountId)
                .withSenseId(senseId)
                .withAudioIndentifier(audioUUID)
                .withDateTimeUTC(speechCreated)
                .build();

        // save to Kinesis
        speechKinesisProducer.addResult(speechResult, SpeechResultsKinesis.SpeechResultsData.Action.TIMELINE, body);

        try {
            final SpeechServiceResult resp = speechClient.stream(body, sampling);


            // try to execute command in transcript
            if(resp.getTranscript().isPresent()) {
                // TODO: save transcript to Kinesis

                executeResult = handlerExecutor.handle(senseId, accountId, resp.getTranscript().get());

                // TODO: save execution results to Kinesis
            }

            if (executeResult.handlerType.equals(HandlerType.WEATHER)) {
                return watsonResponseBuilder.response(Response.SpeechResponse.Result.OK, includeProtobuf, executeResult, responseParam);
            }

            // TODO: response-builder
            if (!executeResult.handlerType.equals(HandlerType.NONE)) {
                return s3ResponseBuilder.response(Response.SpeechResponse.Result.OK, includeProtobuf, executeResult, responseParam);
            } else {
                return s3ResponseBuilder.response(Response.SpeechResponse.Result.TRY_AGAIN, includeProtobuf, executeResult, responseParam);
            }
        } catch (Exception e) {
            LOGGER.error("action=streaming error={}", e.getMessage());
        }

        return s3ResponseBuilder.response(Response.SpeechResponse.Result.REJECTED, includeProtobuf, executeResult, responseParam);
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
            return s3ResponseBuilder.response(Response.SpeechResponse.Result.REJECTED, false, HandlerResult.emptyResult(), responseParam);
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
                return watsonResponseBuilder.response(Response.SpeechResponse.Result.OK, false, executeResult, responseParam);
            }

            // TODO: response-builder
            if (!executeResult.handlerType.equals(HandlerType.NONE)) {
                return s3ResponseBuilder.response(Response.SpeechResponse.Result.OK, false, executeResult, responseParam);
            }
            return s3ResponseBuilder.response(Response.SpeechResponse.Result.TRY_AGAIN, false, executeResult, responseParam);
        } catch (Exception e) {
            LOGGER.error("action=streaming error={}", e.getMessage());
        }

        return s3ResponseBuilder.response(Response.SpeechResponse.Result.REJECTED, false, HandlerResult.emptyResult(), responseParam);
    }

}
