package is.hello.speech.resources.v1;

/**
 * Created by ksg on 9/8/16
 */
public class InvalidSignedBodyException extends RuntimeException {
    InvalidSignedBodyException(final String message) {
        super(message);
    }
}
