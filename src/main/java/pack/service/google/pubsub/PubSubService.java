package pack.service.google.pubsub;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.WatchRequest;
import com.google.api.services.gmail.model.WatchResponse;
import com.google.cloud.ServiceOptions;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.SubscriptionName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pack.ApplicationConfiguration;
import pack.service.google.gmail.GmailApiService;
import pack.service.google.gmail.GmailService;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static pack.service.google.gmail.GmailApiService.PATH_CREDENTIALS;

/**
 * Created by User on 10/13/2017.
 */
@Component
public class PubSubService {

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());

    @Autowired ApplicationConfiguration applicationConfiguration;

    // * Directory to store user credentials for this application.
    // private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"), ".credentials/gmail-java-quickstart");
    private static final java.io.File DATA_STORE_DIR = new java.io.File(PATH_CREDENTIALS);

    // * Global instance of the {@link FileDataStoreFactory}.
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    // TODO remove?
    // private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    // private static HttpTransport HTTP_TRANSPORT;

    @Autowired MessageReceiverPersistData messageReceiver;
    @Autowired GmailApiService gmailApiService;

    private static final String PROJECT_ID = ServiceOptions.getDefaultProjectId(); // TODO what does this resolve to?
    private static final String API_ROLE_PUBLISH = "pubsub.publisher";
    private static final String API_GMAIL_SERVICE_ACCOUNT = "gmail-api-push@system.gserviceaccount.com"; // Account defined on google's side representing pub/sub functionality

    private static Subscriber subscriber;

    static {
        log.info("Static Initializer");
        try {
            // HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    public void preparePrerequisites() throws IOException {
        // TopicService.deleteTopic(projectId, topicId); // Delete topic programmatically
        String pubsubTopicId = applicationConfiguration.pubsubTopicId;
        String pubsubSubscriptionId = applicationConfiguration.pubsubSubscriptionId;

        boolean topicExists = TopicService.topicExists(PROJECT_ID, pubsubTopicId);
        if (topicExists == false) {
            System.out.format("Topic doesn't exist, creating it (project: %s topic: %s)%n", PROJECT_ID, pubsubTopicId);
            TopicService.createTopic(PROJECT_ID, pubsubTopicId);
        }

        boolean subscriptionExists = SubscriptionService.subscriptionExistsWithName(PROJECT_ID, pubsubSubscriptionId);
        if (subscriptionExists) {
            boolean subscriptionExistsForTopic = SubscriptionService.subscriptionExistsForTopic(PROJECT_ID, pubsubSubscriptionId, pubsubTopicId);

            if (subscriptionExistsForTopic == false) {
                // Subscription exists but does not point at the correct topic
                System.out.format("Deleting existing subscription because it does not point to the correct topic: %s%n", pubsubSubscriptionId);
                SubscriptionService.deleteSubscription(PROJECT_ID, pubsubSubscriptionId);
                subscriptionExists = false;
            }
        }

        // Do not combine with above 'if' statement
        if (subscriptionExists == false) {
            log.info("Creating new subscription because none exists");
            SubscriptionService.createSubscription(PROJECT_ID, pubsubTopicId, pubsubSubscriptionId);
        }

        // PermissionService.setEmptyPolicy(projectId, topicId); // Wipe out all access policies on this resource
        // Google API maintains a distinction between role and permissions
        boolean publishRoleExists = PermissionService.checkRole(PROJECT_ID, pubsubTopicId, "roles/" + API_ROLE_PUBLISH, "serviceAccount:" + API_GMAIL_SERVICE_ACCOUNT);

        if (publishRoleExists == false) {
            System.out.format("Publisher role does not exist on gmail service account, will create it %n");

            // Grant publish rights on topic to gmail's service account
            PermissionService.addRole(PROJECT_ID, pubsubTopicId, API_ROLE_PUBLISH, API_GMAIL_SERVICE_ACCOUNT);
            System.out.format("Created role on service account - project: %s  topic: %s  role: %s  serviceAccount: %s%n", PROJECT_ID, pubsubTopicId, API_ROLE_PUBLISH, API_GMAIL_SERVICE_ACCOUNT);
        }
    }

    public void watchMailbox(String googleUserId) throws IOException {
        String pubsubTopicId = applicationConfiguration.pubsubTopicId;

        Gmail gmailServiceForUser = gmailApiService.getGmailService(googleUserId);
        // gmailGetLabels(gmailServiceForUser, user);  // Lists all labels, this just demonstrates that Gmail API and token is working
        gmailUpdateWatch(pubsubTopicId, gmailServiceForUser, googleUserId); // Watch must be called or pull/asyncpull will do nothing
    }

    // Will receive updates from all user mailboxes being watched
    public void startAsyncPull() {
        SubscriptionName subscriptionName = SubscriptionName.of(PROJECT_ID, applicationConfiguration.pubsubSubscriptionId);
        // Need to check if subscription exists

        log.info("Starting async pull on subscription name: " + subscriptionName.toString());
        messageReceiver.setAsyncPullTimeStarted(System.currentTimeMillis());
        subscriber = Subscriber.newBuilder(subscriptionName, messageReceiver).build();
        subscriber.startAsync();
    }

    private void asyncListenStop() {
        if (subscriber != null) {
            subscriber.stopAsync();
            log.info("Async listen stopped");
            subscriber = null;
        }
    }

    private void gmailUpdateWatch(String topicId, Gmail gmailService, String user) {
        WatchRequest watchRequest = new WatchRequest();
        List<String> labelIds = new ArrayList<>();
        labelIds.add("INBOX");
        watchRequest.setLabelIds(labelIds);

        //TODO if re-run, consider using TopicName.create()
        String fullTopicName = "projects/" + PROJECT_ID + "/topics/" + topicId;
        watchRequest.setTopicName(fullTopicName);

        try {
            Gmail.Users.Watch watch = gmailService.users().watch(user, watchRequest);
            log.info("Executing watch request for user: " + user + " topic: " + fullTopicName);

            WatchResponse watchResponse = watch.execute(); // Sometimes fails mysteriously... in worst case, log-out the user if this fails
            BigInteger historyId = watchResponse.getHistoryId();
            Long expiration = watchResponse.getExpiration();

            // Watch request completed, historyId: 6308287, expiration: 1508560031442
            log.info("Watch request completed, historyId: " + historyId + ", expiration: " + expiration);
        } catch (IOException e) {
            // TODO This is where we would attempt to refresh token and retry, or give up and logout user
            // See if this happens again now that authentication settings have been changed
            // Google API should use the refresh token to obtain a new access token
            log.info("Exception setting up watch request: " + e.getMessage());
        }

    }

    private void gmailGetLabels(Gmail gmailService, String user) throws IOException {
        // Print the labels in the user's account.
        ListLabelsResponse listResponse =
                gmailService.users().labels().list(user).execute();
        List<Label> labels = listResponse.getLabels();
        if (labels.size() == 0) {
            log.info("No labels found.");
        } else {
            log.info("Labels:");
            for (Label label : labels) {
                System.out.printf("- %s\n", label.getName());
            }
        }
    }
}
