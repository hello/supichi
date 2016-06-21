package is.hello.speech.resources.v1;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.util.Md5Utils;
import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import is.hello.speech.api.Response;
import is.hello.speech.clients.AsyncSpeechClient;
import is.hello.speech.core.handlers.BaseHandler;
import is.hello.speech.core.handlers.HandlerFactory;
import is.hello.speech.core.models.SpeechResult;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;


@Path("/upload")
@Produces(MediaType.APPLICATION_JSON)
public class UploadResource {

    private final static Logger LOGGER = LoggerFactory.getLogger(UploadResource.class);
    private final static Integer SAMPLING = 8000; // 16000;
    private final AmazonS3 s3;
    private final String bucketName;
    private final AsyncSpeechClient asyncSpeechClient;

    private final HandlerFactory handlerFactory;

    public UploadResource(final AmazonS3 s3, final String bucketName, final AsyncSpeechClient asyncSpeechClient, final HandlerFactory factory) {
        this.s3 = s3;
        this.bucketName = bucketName;
        this.asyncSpeechClient = asyncSpeechClient;
        this.handlerFactory = factory;
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

    @Path("/pb")
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public byte[] streaming(final InputStream inputStream,
                            @DefaultValue("8000") @QueryParam("r") final Integer sampling
    ) throws InterruptedException, IOException {

        try {
            final SpeechResult resp = asyncSpeechClient.stream(inputStream, sampling);

            // try to execute command in transcript
            Boolean executeResult = false;
            if(resp.getTranscript().isPresent()) {
                // get bi-gram commands
                final String[] unigrams = resp.getTranscript().get().toLowerCase().split(" ");
                for (int i = 0; i < (unigrams.length - 1); i++) {
                    final String commandText = String.format("%s %s", unigrams[i], unigrams[i+1]);
                    LOGGER.debug("action=transcribed-command command={}", commandText);

                    final Optional<BaseHandler> optionalHandler = handlerFactory.getHandler(commandText);
                    if (optionalHandler.isPresent()) {
                        final BaseHandler handler = optionalHandler.get();
                        LOGGER.debug("action=found-handler handler={}", handler.getClass().toString());

                        executeResult = handler.executionCommand(commandText, "8AF6441AF72321F4", 2095L);
                        LOGGER.debug("action=execute-command result={}", executeResult);
                        break;
                    } else {
                        LOGGER.info("action=no-handler-found-for-command command={}", commandText);
                    }

                }
            }

            if (executeResult) {
                return response(Response.SpeechResponse.Result.OK, resp.getTranscript().get());
            } else {
                return response(Response.SpeechResponse.Result.TRY_AGAIN, "Did not understand");
            }
        } catch (Exception e) {
            LOGGER.error("error={}", e.getMessage());
        }

        return response(Response.SpeechResponse.Result.REJECTED, "Failed. Try again");
    }


    /**
     * Creates a protobuf response
     * @param result transciption result
     * @param text text to return to Sense
     * @return bytes
     * @throws IOException
     */
    private byte[] response(final Response.SpeechResponse.Result result, final String text) throws IOException {
        Map<Response.SpeechResponse.Result, String> files = ImmutableMap.<Response.SpeechResponse.Result,String>builder()
                .put(Response.SpeechResponse.Result.OK, "emma-16k.vox")
                .put(Response.SpeechResponse.Result.REJECTED, "emma-16k.vox")
                .put(Response.SpeechResponse.Result.TRY_AGAIN, "emma-16k.vox")
                .build();

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final Response.SpeechResponse response = Response.SpeechResponse.newBuilder()
                .setUrl("http://s3.amazonaws.com/hello-audio/voice/" + files.get(result))
                .setResult(result)
                .setText(text)
                .build();
        LOGGER.info("size: {}", response.getSerializedSize());
        response.writeDelimitedTo(outputStream);

        return outputStream.toByteArray();
    }

}
