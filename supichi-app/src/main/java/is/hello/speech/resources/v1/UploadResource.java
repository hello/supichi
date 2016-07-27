package is.hello.speech.resources.v1;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.util.Md5Utils;
import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.util.HelloHttpHeader;
import is.hello.speech.clients.AsyncSpeechClient;
import is.hello.speech.core.api.Response;
import is.hello.speech.core.handlers.BaseHandler;
import is.hello.speech.core.handlers.HandlerFactory;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.SpeechServiceResult;
import is.hello.speech.utils.ResponseBuilder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
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
import java.io.InputStream;


@Path("/upload")
@Produces(MediaType.APPLICATION_JSON)
public class UploadResource {

    private final static Logger LOGGER = LoggerFactory.getLogger(UploadResource.class);
    private final static Integer SAMPLING = 8000; // 16000;
    private final int RAW_AUDIO_BYTES_START = 44; // 60;

    private final AmazonS3 s3;
    private final String bucketName;
    private final AsyncSpeechClient asyncSpeechClient;

    private final HandlerFactory handlerFactory;

    private final DeviceDAO deviceDAO;

    private final ResponseBuilder responseBuilder;

    @Context
    HttpServletRequest request;

    public UploadResource(final AmazonS3 s3, final String bucketName,
                          final AsyncSpeechClient asyncSpeechClient,
                          final HandlerFactory factory,
                          final DeviceDAO deviceDAO,
                          final ResponseBuilder responseBuilder) {
        this.s3 = s3;
        this.bucketName = bucketName;
        this.asyncSpeechClient = asyncSpeechClient;
        this.handlerFactory = factory;
        this.deviceDAO = deviceDAO;
        this.responseBuilder = responseBuilder;
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


    @Path("/google")
    @POST
    @Timed
    public String speech(byte[] body) throws InterruptedException, IOException {

        final Optional<String> resp = asyncSpeechClient.recognize(body, SAMPLING);
        return resp.or("failed");
    }

    @Path("/audio")
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public byte[] streamingAudio(final InputStream inputStream,
                            @DefaultValue("8000") @QueryParam("r") final Integer sampling
    ) throws InterruptedException, IOException {
        return streaming(inputStream, sampling, false);
    }

    @Path("/pb")
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public byte[] streaming(final InputStream inputStream,
                            @DefaultValue("8000") @QueryParam("r") final Integer sampling,
                            @DefaultValue("true") @QueryParam("pb") final boolean includeProtobuf
    ) throws InterruptedException, IOException {

        HandlerResult executeResult = HandlerResult.emptyResult();

        final String debugSenseId = this.request.getHeader(HelloHttpHeader.SENSE_ID);
        final String senseId = (debugSenseId == null) ? "8AF6441AF72321F4" : debugSenseId;
        final ImmutableList<DeviceAccountPair> accounts = deviceDAO.getAccountIdsForDeviceId(senseId);

        if (accounts.isEmpty()) {
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
            final SpeechServiceResult resp = asyncSpeechClient.stream(inputStream, sampling);

            // try to execute command in transcript
            if(resp.getTranscript().isPresent()) {

                // get bi-gram commands
                final String[] unigrams = resp.getTranscript().get().toLowerCase().split(" ");

                for (int i = 0; i < (unigrams.length - 1); i++) {
                    final String commandText = String.format("%s %s", unigrams[i], unigrams[i+1]);
                    LOGGER.debug("action=get-transcribed-command text={}", commandText);

                    // TODO: command-parser
                    final Optional<BaseHandler> optionalHandler = handlerFactory.getHandler(commandText);

                    if (optionalHandler.isPresent()) {
                        final BaseHandler handler = optionalHandler.get();
                        LOGGER.debug("action=find-handler result=success handler={}", handler.getClass().toString());

                        executeResult = handler.executionCommand(commandText, senseId, accountId);
                        LOGGER.debug("action=execute-command result={}", executeResult);
                        break;
                    } else {
                        LOGGER.info("action=find-handler result=fail text=\"{}\"", commandText);
                    }
                }
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

}
