package is.hello.speech.resources.v1;

import com.google.common.base.Optional;
import com.hello.suripu.core.db.KeyStore;
import is.hello.speech.utils.HmacSignedMessage;

import javax.ws.rs.WebApplicationException;
import java.util.Arrays;

public class SignedBodyHandler {

    private final KeyStore keystore;

    public SignedBodyHandler(final KeyStore keystore) {
        this.keystore = keystore;
    }

    public byte[] extractAudio(final String senseId, byte[] body) {
        final Optional<byte[]> optionalKey = keystore.getStrict(senseId);
        if(!optionalKey.isPresent()) {
            throw new WebApplicationException(javax.ws.rs.core.Response.Status.BAD_REQUEST);
        }

        if(body.length <= 20) {
            throw new WebApplicationException(javax.ws.rs.core.Response.Status.BAD_REQUEST);
        }

        final byte[] sig = Arrays.copyOfRange(body, body.length - 20, body.length);
        final byte[] audioBody = Arrays.copyOfRange(body, 0, body.length-20);

        if(!HmacSignedMessage.match(audioBody, optionalKey.get(), sig)) {
            throw new WebApplicationException(javax.ws.rs.core.Response.Status.UNAUTHORIZED);
        }

        return audioBody;
    }
}
