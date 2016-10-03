package is.hello.speech.core.response;

import is.hello.speech.core.api.Response;
import is.hello.speech.core.api.Speech;
import is.hello.speech.core.models.HandlerResult;

public interface SupichiResponseBuilder {

    byte[] response(final Response.SpeechResponse.Result result,
                    final HandlerResult handlerResult,
                    final Speech.SpeechRequest request);
}
