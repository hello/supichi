package is.hello.speech.resources.v1;

import com.google.api.client.util.Maps;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.KeyStore;
import is.hello.speech.clients.SpeechClient;
import is.hello.speech.core.handlers.executors.HandlerExecutor;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.UploadResponseParam;
import is.hello.speech.core.response.SupichiResponseBuilder;
import is.hello.speech.core.response.SupichiResponseType;
import is.hello.speech.kinesis.SpeechKinesisProducer;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Map;

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
    private SignedBodyHandler signedBodyHandler;
    private UploadResponseParam responseParam = new UploadResponseParam("adpcm");

    final Map<HandlerType, SupichiResponseType> handlersToBuilders = Maps.newHashMap();
    final Map<SupichiResponseType, SupichiResponseBuilder> responseBuilders = Maps.newHashMap();

    @Before
    public void setUp() {
        client = mock(SpeechClient.class);
        keystore = mock(KeyStore.class);
        executor = mock(HandlerExecutor.class);
        deviceDAO = mock(DeviceDAO.class);
        signedBodyHandler = mock(SignedBodyHandler.class);
        kinesisProducer = mock(SpeechKinesisProducer.class);

    }

    @Test(expected = WebApplicationException.class)
    public void testUploadSignedAudioNoKey() throws InvalidSignedBodyException, InvalidSignatureException, IOException, InterruptedException {
        when(keystore.getStrict(any(String.class))).thenReturn(Optional.absent());
        when(signedBodyHandler.extractSignature(any(String.class), any(byte[].class))).thenReturn(new byte[]{});
        when(deviceDAO.getAccountIdsForDeviceId(any(String.class))).thenReturn(ImmutableList.of());
        final UploadResource resource = new UploadResource(client, signedBodyHandler, executor,
                deviceDAO, kinesisProducer, responseBuilders, handlersToBuilders);

        final HttpServletRequest request = mock(HttpServletRequest.class);
        resource.request = request;

        resource.streaming(new byte[]{}, 8000, false, responseParam);
    }

    public void testUploadSignedAudioInvalid() throws IOException, InterruptedException, InvalidSignedBodyException, InvalidSignatureException {
        when(keystore.getStrict(any(String.class))).thenReturn(Optional.of(new byte[]{}));
        when(signedBodyHandler.extractSignature(any(String.class), any(byte[].class))).thenReturn(new byte[]{});
        when(deviceDAO.getAccountIdsForDeviceId(any(String.class))).thenReturn(ImmutableList.of());
        final UploadResource resource = new UploadResource(client, signedBodyHandler, executor,
                deviceDAO, kinesisProducer, responseBuilders, handlersToBuilders);

        final HttpServletRequest request = mock(HttpServletRequest.class);
        resource.request = request;

        try {
            resource.streaming(new byte[]{}, 8000, false, responseParam);
        }catch (WebApplicationException w) {
            assertEquals(w.getResponse().getStatus(), Response.Status.BAD_REQUEST);
        }
    }
}