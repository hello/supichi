package is.hello.speech.resources.v1;

/**
 * Created by ksg on 9/12/16
 */
public class InvalidSignatureException extends RuntimeException {
    InvalidSignatureException(final String message) {
        super(message);
    }
}
