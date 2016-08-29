package is.hello.speech.core.response;

import is.hello.speech.core.api.Response;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.UploadResponseParam;

public interface SupichiResponseBuilder {

    byte[] response(final Response.SpeechResponse.Result result,
                    final boolean includeProtobuf,
                    final HandlerResult handlerResult,
                    final UploadResponseParam responseParam);
}
