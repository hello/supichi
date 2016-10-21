package is.hello.speech.core.response;

import is.hello.speech.core.models.HandlerResult;
import is.hello.supichi.api.Response;
import is.hello.supichi.api.Speech;

public interface SupichiResponseBuilder {

    byte[] response(final Response.SpeechResponse.Result result,
                    final HandlerResult handlerResult,
                    final Speech.SpeechRequest request);
}
