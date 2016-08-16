package is.hello.speech.resources.v1;

import com.google.common.base.Optional;
import com.hello.suripu.core.db.KeyStore;
import org.junit.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SignedBodyHandlerTest {

    final KeyStore keystore = mock(KeyStore.class);

    @Test(expected = WebApplicationException.class)
    public void testUploadSignedAudioNoKey() throws IOException, InterruptedException {
        when(keystore.getStrict(any(String.class))).thenReturn(Optional.absent());

        final SignedBodyHandler signedBodyHandler = new SignedBodyHandler(keystore);
        signedBodyHandler.extractAudio("sense", new byte[]{});
    }

    @Test(expected = WebApplicationException.class)
    public void testUploadSignedAudioInvalid() throws IOException, InterruptedException {
        when(keystore.getStrict(any(String.class))).thenReturn(Optional.of(new byte[]{}));

        final SignedBodyHandler signedBodyHandler = new SignedBodyHandler(keystore);

        try {
            signedBodyHandler.extractAudio("sense", new byte[]{});
        }catch (WebApplicationException w) {
            assertEquals(w.getResponse().getStatusInfo().getStatusCode(), Response.Status.BAD_REQUEST.getStatusCode());
            throw w;
        }
    }


    @Test(expected = WebApplicationException.class)
    public void testUploadSignedAudioContentTooShort() throws IOException, InterruptedException {
        when(keystore.getStrict(any(String.class))).thenReturn(Optional.of(new byte[]{}));

        final SignedBodyHandler signedBodyHandler = new SignedBodyHandler(keystore);

        try {
            signedBodyHandler.extractAudio("sense", new byte[]{});
        }catch (WebApplicationException w) {
            assertEquals(w.getResponse().getStatusInfo().getStatusCode(), Response.Status.BAD_REQUEST.getStatusCode());
            throw w;
        }
    }
}
