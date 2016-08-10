package is.hello.speech.resources.v1;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.util.Md5Utils;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableList;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.util.HelloHttpHeader;
import is.hello.speech.clients.SpeechClient;
import is.hello.speech.core.api.Response;
import is.hello.speech.core.handlers.executors.HandlerExecutor;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechServiceResult;
import is.hello.speech.core.models.TextQuery;
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
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;


@Path("/upload")
@Produces(MediaType.APPLICATION_JSON)
public class UploadResource {

    private final static Logger LOGGER = LoggerFactory.getLogger(UploadResource.class);
    private final static Integer SAMPLING = 8000; // 16000;
    private final int RAW_AUDIO_BYTES_START = 44; // 60;

    private final AmazonS3 s3;
    private final String bucketName;
    private final SpeechClient speechClient;

    private final HandlerExecutor handlerExecutor;

    private final DeviceDAO deviceDAO;

    private final ResponseBuilder responseBuilder;
    private final WatsonResponseBuilder watsonResponseBuilder;

    @Context
    HttpServletRequest request;

    public UploadResource(final AmazonS3 s3, final String bucketName,
                          final SpeechClient speechClient,
                          final HandlerExecutor handlerExecutor,
                          final DeviceDAO deviceDAO,
                          final ResponseBuilder responseBuilder,
                          final WatsonResponseBuilder watsonResponseBuilder) {
        this.s3 = s3;
        this.bucketName = bucketName;
        this.speechClient = speechClient;
        this.handlerExecutor = handlerExecutor;
        this.deviceDAO = deviceDAO;
        this.responseBuilder = responseBuilder;
        this.watsonResponseBuilder = watsonResponseBuilder;
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
                            @DefaultValue("false") @QueryParam("pb") final boolean includeProtobuf
    ) throws InterruptedException, IOException {

        LOGGER.debug("action=received-bytes size={}", body.length);

        HandlerResult executeResult = HandlerResult.emptyResult();

        final String debugSenseId = this.request.getHeader(HelloHttpHeader.SENSE_ID);
        // old default: 8AF6441AF72321F4  C8DAAC353AEFA4A9
        final String senseId = (debugSenseId == null || debugSenseId.equals("0000000000000000")) ? "8AF6441AF72321F4" : debugSenseId;
        final ImmutableList<DeviceAccountPair> accounts = deviceDAO.getAccountIdsForDeviceId(senseId);

        LOGGER.debug("info=sense-id id={}", senseId);
        if (accounts.isEmpty()) {
            LOGGER.error("error=no-paired-sense-found sense_id={}", senseId);
            return responseBuilder.response(Response.SpeechResponse.Result.REJECTED, includeProtobuf, executeResult);
        }

        // TODO: for now, pick the smallest account-id as the primary id
        Long accountId = accounts.get(0).accountId;
        for (final DeviceAccountPair accountPair : accounts) {
            if (accountPair.accountId < accountId) {
                accountId = accountPair.accountId;
            }
        }

        LOGGER.debug("action=get-speech-audio sense_id={} account_id={}", senseId, accountId);
        try {
            final SpeechServiceResult resp = speechClient.stream(body, sampling);

            // try to execute command in transcript
            if(resp.getTranscript().isPresent()) {

                executeResult = handlerExecutor.handle(senseId, accountId, resp.getTranscript().get());
            }

            if (executeResult.handlerType.equals(HandlerType.WEATHER)) {
                return watsonResponseBuilder.response(executeResult);
            }

            // TODO: response-builder
            if (!executeResult.handlerType.equals(HandlerType.NONE)) {
                return responseBuilder.response(Response.SpeechResponse.Result.OK, includeProtobuf, executeResult);
            } else {
                return responseBuilder.response(Response.SpeechResponse.Result.TRY_AGAIN, includeProtobuf, executeResult);
            }
        } catch (Exception e) {
            LOGGER.error("action=streaming error={}", e.getMessage());
        }

        return responseBuilder.response(Response.SpeechResponse.Result.REJECTED, includeProtobuf, executeResult);
    }

    @Path("/text")
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public byte[] text(@Valid final TextQuery query) throws InterruptedException, IOException {

        final ImmutableList<DeviceAccountPair> accounts = deviceDAO.getAccountIdsForDeviceId(query.senseId);

        LOGGER.debug("info=sense-id id={}", query.senseId);
        if (accounts.isEmpty()) {
            LOGGER.error("error=no-paired-sense-found sense_id={}", query.senseId);
            return responseBuilder.response(Response.SpeechResponse.Result.REJECTED, false, HandlerResult.emptyResult());
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
                return watsonResponseBuilder.response(executeResult);
            }

            // TODO: response-builder
            if (!executeResult.handlerType.equals(HandlerType.NONE)) {
                return responseBuilder.response(Response.SpeechResponse.Result.OK, false, executeResult);
            }
            return responseBuilder.response(Response.SpeechResponse.Result.TRY_AGAIN, false, executeResult);
        } catch (Exception e) {
            LOGGER.error("action=streaming error={}", e.getMessage());
        }

        return responseBuilder.response(Response.SpeechResponse.Result.REJECTED, false, HandlerResult.emptyResult());
    }

}
