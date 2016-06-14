package is.hello.speech.server;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class SpeechApplication extends Application<SpeechConfiguration> {

    public static void main(String[] args) throws Exception {
        new SpeechApplication().run(args);
    }

    @Override
    public String getName() {
        return "hello-world";
    }

    @Override
    public void initialize(Bootstrap<SpeechConfiguration> bootstrap) {
        // nothing to do yet
    }


    @Override
    public void run(SpeechConfiguration speechConfiguration, Environment environment) throws Exception {


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

        final BlockingSpeechClient blocking = new BlockingSpeechClient(host, port);


        environment.lifecycle().manage(speechClientManaged);
        environment.jersey().register(new UploadResource(amazonS3, speechConfiguration.s3Bucket(), client, blocking));
    }
}
