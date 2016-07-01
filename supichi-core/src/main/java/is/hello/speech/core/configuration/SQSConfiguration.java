package is.hello.speech.core.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;

/**
 * Created by kingshy on 1/11/16
 */
public class SQSConfiguration {

    @NotNull
    @JsonProperty("sqs_queue_name")
    private String sqsQueueName = null;
    public String getSqsQueueName() { return this.sqsQueueName; }
    public void setSqsQueueName(final String sqsQueueName) { this.sqsQueueName = sqsQueueName; }

    @NotNull
    @JsonProperty("sqs_queue_url")
    private String sqsQueueUrl = null;
    public String getSqsQueueUrl() { return this.sqsQueueUrl; }
    public void setSqsQueueUrl(final String sqsQueueUrl) { this.sqsQueueUrl = sqsQueueUrl; }

    @NotNull
    @JsonProperty("sqs_max_connections")
    private int sqsMaxConnections = 50;
    public int getSqsMaxConnections() { return this.sqsMaxConnections; }
    public void setSqsMaxConnections(final int sqsMaxConnections) { this.sqsMaxConnections = sqsMaxConnections; }

    @NotNull
    @JsonProperty("sqs_max_messages_read")
    private int sqsMaxMessage = 10;
    public int getSqsMaxMessage() { return this.sqsMaxMessage; }
    public void setSqsMaxMessage(final int sqsMaxMessage) { this.sqsMaxMessage = sqsMaxMessage; }

    @NotNull
    @JsonProperty("sqs_visibility_timeout_seconds")
    private int sqsVisibilityTimeoutSeconds = 60;
    public int getSqsVisibilityTimeoutSeconds() { return this.sqsVisibilityTimeoutSeconds; }
    public void setSqsVisibilityTimeoutSeconds(final int sqsVisibilityTimeoutSeconds) { this.sqsVisibilityTimeoutSeconds = sqsVisibilityTimeoutSeconds; }

    @NotNull
    @JsonProperty("sqs_wait_time_seconds")
    private int sqsWaitTimeSeconds = 30;
    public int getSqsWaitTimeSeconds() { return this.sqsWaitTimeSeconds; }
    public void setSqsWaitTimeSeconds(final int sqsWaitTimeSeconds) { this.sqsWaitTimeSeconds = sqsWaitTimeSeconds; }

    @JsonProperty("sqs_extra_queue_name")
    private String sqsExtraQueueName = null;
    public String getSqsExtraQueueName() { return this.sqsExtraQueueName; }
    public void setSqsExtraQueueName(final String sqsExtraQueueName) { this.sqsExtraQueueName = sqsExtraQueueName; }


}
