package is.hello.speech;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import is.hello.speech.clients.AsyncSpeechClient;
import is.hello.speech.clients.SpeechClientManaged;
import is.hello.speech.configuration.SpeechAppConfiguration;
import is.hello.speech.resources.v1.UploadResource;

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


        final AWSCredentials awsCredentials = new AWSCredentials() {

            public String getAWSAccessKeyId() {
                return "AKIAIPS2RBLAWR6TNSPA";
            }

            public String getAWSSecretKey() {
                return "eQ56jKPGVYzSyuZ7AMD6cHkR84wswGGK7TtJ4qOL";
            }
        };

        final AmazonS3 amazonS3 = new AmazonS3Client(awsCredentials);

        String host = "speech.googleapis.com";
        Integer port = 443;

        final AsyncSpeechClient client =
                new AsyncSpeechClient(host, port);
        final SpeechClientManaged speechClientManaged = new SpeechClientManaged(client);

        environment.lifecycle().manage(speechClientManaged);
        environment.jersey().register(new UploadResource(amazonS3, speechAppConfiguration.s3Bucket(), client));
    }
}
