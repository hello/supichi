package is.hello.speech.resources.v1;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.KeyStore;
import is.hello.speech.clients.SpeechClient;
import is.hello.speech.core.handlers.executors.HandlerExecutor;
import is.hello.speech.kinesis.SpeechKinesisProducer;
import is.hello.speech.utils.ResponseBuilder;
import is.hello.speech.utils.WatsonResponseBuilder;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UploadResourceTest {

    private SpeechClient client;
    private KeyStore keystore;
    private HandlerExecutor executor;
    private DeviceDAO deviceDAO;
    private SpeechKinesisProducer kinesisProducer;
    private ResponseBuilder responseBuilder;
    private WatsonResponseBuilder watsonResponseBuilder;
    private SignedBodyHandler signedBodyHandler;

    @Before
    public void setUp() {
        client = mock(SpeechClient.class);
        keystore = mock(KeyStore.class);
        executor = mock(HandlerExecutor.class);
        deviceDAO = mock(DeviceDAO.class);
        responseBuilder = mock(ResponseBuilder.class);
        watsonResponseBuilder = mock(WatsonResponseBuilder.class);
        signedBodyHandler = mock(SignedBodyHandler.class);
        kinesisProducer = mock(SpeechKinesisProducer.class);

    }

    @Test(expected = WebApplicationException.class)
    public void testUploadSignedAudioNoKey() throws IOException, InterruptedException {
        when(keystore.getStrict(any(String.class))).thenReturn(Optional.absent());
        when(signedBodyHandler.extractAudio(any(String.class), any(byte[].class))).thenReturn(new byte[]{});
        when(deviceDAO.getAccountIdsForDeviceId(any(String.class))).thenReturn(ImmutableList.of());
        final UploadResource resource = new UploadResource(client, signedBodyHandler, executor,
                deviceDAO, kinesisProducer, responseBuilder, watsonResponseBuilder);

        final HttpServletRequest request = mock(HttpServletRequest.class);
        resource.request = request;

        resource.streaming(new byte[]{}, 8000, false);
    }

    public void testUploadSignedAudioInvalid() throws IOException, InterruptedException {
        when(keystore.getStrict(any(String.class))).thenReturn(Optional.of(new byte[]{}));
        when(signedBodyHandler.extractAudio(any(String.class), any(byte[].class))).thenReturn(new byte[]{});
        when(deviceDAO.getAccountIdsForDeviceId(any(String.class))).thenReturn(ImmutableList.of());
        final UploadResource resource = new UploadResource(client, signedBodyHandler, executor,
                deviceDAO, kinesisProducer, responseBuilder, watsonResponseBuilder);

        final HttpServletRequest request = mock(HttpServletRequest.class);
        resource.request = request;

        try {
            resource.streaming(new byte[]{}, 8000, false);
        }catch (WebApplicationException w) {
            assertEquals(w.getResponse().getStatus(), Response.Status.BAD_REQUEST);
        }
    }
}
