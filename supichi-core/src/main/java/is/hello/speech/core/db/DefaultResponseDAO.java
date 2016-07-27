package is.hello.speech.core.db;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import is.hello.speech.core.api.Response;
import is.hello.speech.core.models.UploadResponse;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


/**
 * Created by ksg on 7/13/16
 */
public class DefaultResponseDAO {
    private final static Logger LOGGER = LoggerFactory.getLogger(DefaultResponseDAO.class);

    private final static int HEADER_SIZE = 44;

    private final static Map<Response.SpeechResponse.Result, String> DEFAULT_KEYNAMES = new HashMap<Response.SpeechResponse.Result, String>() {{
        put(Response.SpeechResponse.Result.OK, "default_ok-WATSON-MICHAEL-compressed.ima");
        put(Response.SpeechResponse.Result.REJECTED, "default_rejected-WATSON-MICHAEL-compressed.ima");
        put(Response.SpeechResponse.Result.TRY_AGAIN, "default_try_again-WATSON-MICHAEL-compressed.ima");
        put(Response.SpeechResponse.Result.UNKNOWN, "default_unknown-WATSON-MICHAEL-compressed.ima");
    }};

    private final static Map<Response.SpeechResponse.Result, String> DEFAULT_TEXT = new HashMap<Response.SpeechResponse.Result, String>() {{
        put(Response.SpeechResponse.Result.OK, "OK, it's done.");
        put(Response.SpeechResponse.Result.REJECTED, "Sorry, your command is rejected");
        put(Response.SpeechResponse.Result.TRY_AGAIN, "Sorry, your command cannot be processed. Please try again.");
        put(Response.SpeechResponse.Result.UNKNOWN, "Sorry, we've encountered an error. Please try again later.");
    }};

    private Map<Response.SpeechResponse.Result, UploadResponse> responses;

    public static DefaultResponseDAO create(final AmazonS3 s3, final String bucket) {

        final Map<Response.SpeechResponse.Result, UploadResponse> tmpMap = Maps.newHashMap();

        for (Map.Entry<Response.SpeechResponse.Result, String> entry : DEFAULT_KEYNAMES.entrySet()) {
            final String keyname = entry.getValue();
            final Response.SpeechResponse.Result result = entry.getKey();

            final S3Object object = s3.getObject(bucket, keyname);
            final InputStream inputStream = object.getObjectContent();

            try {
                byte [] bytes = IOUtils.toByteArray(inputStream);

                // remove wav header. see http://forum.doom9.org/archive/index.php/t-20481.html
                // final String audio = new String(bytes);  // audio.indexOf("data") + 8;
                final int audioStartPosition = HEADER_SIZE;

                final String text = DEFAULT_TEXT.get(result);

                final Response.SpeechResponse response = Response.SpeechResponse.newBuilder()
                        .setUrl("http://s3.amazonaws.com/hello-audio/voice/" + keyname)
                        .setResult(result)
                        .setText(text)
                        .setAudioStreamSize(bytes.length - audioStartPosition)
                        .build();


                tmpMap.put(result, new UploadResponse(response, Arrays.copyOfRange(bytes, audioStartPosition, bytes.length)));

            } catch (IOException e) {
                LOGGER.error("error=fail-to-convert-s3-stream-to-bytes error_msg={}", e.getMessage());
            }
        }
        return new DefaultResponseDAO(tmpMap);
    }

    private DefaultResponseDAO(final Map<Response.SpeechResponse.Result, UploadResponse> defaultResponseText) {
        this.responses = ImmutableMap.copyOf(defaultResponseText);
    }

    public UploadResponse getResponse(Response.SpeechResponse.Result result) {
        if (this.responses.containsKey(result)) {
            return this.responses.get(result);
        }
        return this.responses.get(Response.SpeechResponse.Result.UNKNOWN);
    }
}
