package is.hello.speech.resources.v1;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.codahale.metrics.annotation.Timed;
import is.hello.speech.core.api.Text2SpeechQueue;
import is.hello.speech.core.configuration.SQSConfiguration;
import is.hello.speech.core.text2speech.Text2SpeechUtils;
import is.hello.speech.core.text2speech.VoiceResponse;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.security.NoSuchAlgorithmException;

/**
 * Created by ksg on 6/30/16
 */
@Path("/queue")
@Produces(MediaType.APPLICATION_JSON)
public class QueueMessageResource {

    private final static Logger LOGGER = LoggerFactory.getLogger(QueueMessageResource.class);
    private final AmazonSQSAsync sqsClient;
    private final String sqsQueueUrl;
    private final SQSConfiguration sqsConfiguration;

    public QueueMessageResource(final AmazonSQSAsync sqsClient, final String sqsQueueUrl, final SQSConfiguration sqsConfiguration) {
        this.sqsClient = sqsClient;
        this.sqsQueueUrl = sqsQueueUrl;
        this.sqsConfiguration = sqsConfiguration;
    }

    @Path("/send_message/voice_response")
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    public Boolean sendQueueMessage(final VoiceResponse voiceResponse) throws NoSuchAlgorithmException {
        // send message to SQS

        final Text2SpeechQueue.SynthesizeMessage queueMessage = Text2SpeechQueue.SynthesizeMessage.newBuilder()
                .setText(voiceResponse.text)
                .setIntent(Text2SpeechQueue.SynthesizeMessage.IntentType.valueOf(voiceResponse.intent.getValue()))
                .setAction(Text2SpeechQueue.SynthesizeMessage.ActionType.valueOf(voiceResponse.action.getValue()))
                .setCategory(Text2SpeechQueue.SynthesizeMessage.CategoryType.valueOf(voiceResponse.category.getValue()))
                .setParametersString(voiceResponse.parameters)
                .setService(Text2SpeechQueue.SynthesizeMessage.ServiceType.valueOf(voiceResponse.serviceType.getValue()))
                .setVoice(Text2SpeechQueue.SynthesizeMessage.VoiceType.valueOf(voiceResponse.voiceType.getValue()))
                .setResponseType(Text2SpeechQueue.SynthesizeMessage.ResponseType.valueOf(voiceResponse.responseType.getValue()))
                .build();
        final String messageBody = Text2SpeechUtils.encodeMessage(queueMessage);
        final String bodyMD5 = DigestUtils.md5Hex(messageBody);

        final SendMessageRequest sendMessageRequest = new SendMessageRequest(sqsQueueUrl, messageBody);
        final SendMessageResult sentResult = sqsClient.sendMessage(sendMessageRequest);
        final String sentMD5 = sentResult.getMD5OfMessageBody();
        return bodyMD5.equals(sentMD5);
    }

}
