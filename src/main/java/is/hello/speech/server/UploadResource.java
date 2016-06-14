package is.hello.speech.server;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.util.Md5Utils;
import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import is.hello.speech.api.Response;
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
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;


@Path("/upload")
@Produces(MediaType.APPLICATION_JSON)
public class UploadResource {

    private final static Logger LOGGER = LoggerFactory.getLogger(UploadResource.class);
    private final static Integer SAMPLING = 8000; // 16000;
    private final AmazonS3 s3;
    private final String bucketName;
    private final AsyncSpeechClient asyncSpeechClient;
    private final BlockingSpeechClient blockingSpeechClient;

    public UploadResource(final AmazonS3 s3, final String bucketName, final AsyncSpeechClient asyncSpeechClient, final BlockingSpeechClient blockingSpeechClient) {
        this.s3 = s3;
        this.bucketName = bucketName;
        this.asyncSpeechClient = asyncSpeechClient;
        this.blockingSpeechClient = blockingSpeechClient;
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

    @Path("/blocking")
    @POST
    @Timed
    public String blocking(byte[] body) throws InterruptedException, IOException {
        return blockingSpeechClient.recognize(body, SAMPLING);
    }

    @Path("/pb")
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public byte[] streaming(final InputStream inputStream, @DefaultValue("8000") @QueryParam("r") final Integer sampling) throws InterruptedException, IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            final Optional<String> resp = asyncSpeechClient.stream(inputStream, sampling);
            if(resp.isPresent()) {
                final Response.SpeechResponse response = Response.SpeechResponse.newBuilder()
                        .setUrl("http://s3.amazonaws.com/hello-audio/voice/success.raw")
                        .setText(resp.get())
                        .setResult(Response.SpeechResponse.Result.OK)
                        .build();
                LOGGER.info("success size: {}", response.getSerializedSize());
                response.writeDelimitedTo(outputStream);
                return outputStream.toByteArray();
            }
        } catch (Exception e) {
            LOGGER.error("error={}", e.getMessage());
            throw new WebApplicationException(500);
        }

        final Response.SpeechResponse response = Response.SpeechResponse.newBuilder()
                .setUrl("http://s3.amazonaws.com/hello-audio/voice/failure.raw")
                .setResult(Response.SpeechResponse.Result.REJECTED)
                .setText("Failed. Try again")
                .build();
        LOGGER.info("size: {}", response.getSerializedSize());
        response.writeDelimitedTo(outputStream);

        return outputStream.toByteArray();
    }

    @POST
    @Path("/foo")
    public String foo(final InputStream in) {
        try {
            byte[] buffer = new byte[1024];
            int bytesRead;
            int totalBytes = 0;
            while ((bytesRead = in.read(buffer)) != -1) {
                LOGGER.debug("read " + bytesRead + " bytes from input stream");
                final String content = new String(Arrays.copyOf(buffer, bytesRead), "ASCII");
                totalBytes += bytesRead;
                LOGGER.info("Uploading {} to Google", bytesRead);
                LOGGER.info("Uploading {} to Google", content);
//             To simulate real-time audio, sleep after sending each audio buffer.
//             For 16000 Hz sample rate, sleep 100 milliseconds.
//                Thread.sleep(samplingRate / 160);
            }
            LOGGER.info("Sent " + totalBytes + " bytes from audio");
            return "OK";
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }

        return "KO";
    }
}
