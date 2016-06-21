package is.hello.speech;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hello.suripu.coredw8.clients.MessejiClient;
import com.hello.suripu.coredw8.clients.MessejiHttpClient;
import com.hello.suripu.coredw8.configuration.MessejiHttpClientConfiguration;
import io.dropwizard.Application;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import is.hello.speech.clients.AsyncSpeechClient;
import is.hello.speech.clients.SpeechClientManaged;
import is.hello.speech.configuration.SpeechAppConfiguration;
import is.hello.speech.core.handlers.BaseHandler;
import is.hello.speech.core.handlers.HandlerFactory;
import is.hello.speech.core.handlers.SleepSoundHandler;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.resources.v1.UploadResource;

import java.util.Map;

public class SpeechApp extends Application<SpeechAppConfiguration> {

    public static void main(String[] args) throws Exception {
        new SpeechApp().run(args);
    }

    @Override
    public String getName() {
        return "hello-world";
    }

    @Override
    public void initialize(Bootstrap<SpeechAppConfiguration> bootstrap) {
        // nothing to do yet
    }


    @Override
    public void run(SpeechAppConfiguration speechAppConfiguration, Environment environment) throws Exception {

        // sleep sound handler
        final MessejiHttpClientConfiguration messejiHttpClientConfiguration = speechAppConfiguration.getMessejiHttpClientConfiguration();
        final MessejiClient messejiClient = MessejiHttpClient.create(
                new HttpClientBuilder(environment).using(messejiHttpClientConfiguration.getHttpClientConfiguration()).build("messeji"),
                messejiHttpClientConfiguration.getEndpoint());

        final SleepSoundHandler sleepSoundHandler = new SleepSoundHandler(messejiClient, null);


        // set up handlers factory
        Map<HandlerType, BaseHandler> handlerMap = Maps.newHashMap();
        handlerMap.put(HandlerType.SLEEP_SCOUNDS, sleepSoundHandler);

        final HandlerFactory handlerFactory = new HandlerFactory(ImmutableMap.copyOf(handlerMap));


        final AWSCredentials awsCredentials = new AWSCredentials() {
            public String getAWSAccessKeyId() { return speechAppConfiguration.getS3Configuration().getAwsAccessKey(); }
            public String getAWSSecretKey() { return speechAppConfiguration.getS3Configuration().getAwsSecretKey(); }
        };


        final AmazonS3 amazonS3 = new AmazonS3Client(awsCredentials);

        final AsyncSpeechClient client = new AsyncSpeechClient(
                speechAppConfiguration.getGoogleAPIHost(),
                speechAppConfiguration.getGoogleAPIPort(),
                speechAppConfiguration.getAudioConfiguration());

        final SpeechClientManaged speechClientManaged = new SpeechClientManaged(client);

        environment.lifecycle().manage(speechClientManaged);
        environment.jersey().register(new UploadResource(amazonS3, speechAppConfiguration.getS3Configuration().getBucket(), client, handlerFactory));
    }
}
