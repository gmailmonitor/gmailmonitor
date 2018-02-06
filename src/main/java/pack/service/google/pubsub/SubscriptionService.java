package pack.service.google.pubsub;

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Created by User on 10/13/2017.
 */
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());

    public static void createSubscription(String projectId, String topicId, String subscriptionId) throws IOException {
        try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {

            // eg. projectId = "my-test-project", topicId = "my-test-topic"
            TopicName topicName = TopicName.create(projectId, topicId);

            // eg. subscriptionId = "my-test-subscription"
            SubscriptionName subscriptionName = SubscriptionName.create(projectId, subscriptionId);

            // create a pull subscription with default acknowledgement deadline
            log.info("Creating subscription for project: " + projectId + " topic: " + topicId + " subscription: " + subscriptionId);
            Subscription subscription = subscriptionAdminClient.createSubscription(subscriptionName, topicName, PushConfig.getDefaultInstance(), 0);
            log.info("Created subscription: " + subscription.getName());
        } catch (Exception e) {
            throw new RuntimeException("Google auto-closeable threw Exception", e);
        }
    }

    public static void deleteSubscription(String projectId, String subscriptionId) throws IOException {
        try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {

            // eg. subscriptionId = "my-test-subscription"
            SubscriptionName subscriptionName = SubscriptionName.create(projectId, subscriptionId);

            // create a pull subscription with default acknowledgement deadline
            subscriptionAdminClient.deleteSubscription(subscriptionName);
            log.info("Deleted subscription: " + subscriptionName);
        } catch (Exception e) {
            throw new RuntimeException("Google auto-closeable threw exception", e);
        }
    }

    public static boolean subscriptionExistsForTopic(String projectId, String subscriptionId, String topicId) throws IOException {
        SubscriptionName subscriptionName = SubscriptionName.create(projectId, subscriptionId);

        try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
            Subscription subscription = subscriptionAdminClient.getSubscription(subscriptionName);
            String topic = subscription.getTopic();

            if (topic.equals("_deleted-topic_")) {
                System.out.format("Subscription with this name exists,  but points to deleted topic.  name:  %s%n", subscriptionName);
                return false;
            }

            return true;

        } catch (NotFoundException e) {
            System.out.format("%s was thrown, assumed subscription does not exist, SubscriptionName: %s%n", e, subscriptionName);
            return false;
        } catch (IOException e) {
            System.out.format("Something went wrong while checking if subscription exists: %s was thrown.  SubscriptionName: %n", e, subscriptionName);
            throw e;
        } catch (Exception e) {
            String message = String.format("Something went wrong while checking if subscription exists: %s was thrown.  SubscriptionName: %n", e, subscriptionName);
            throw new RuntimeException(message, e);
        }
    }

    public static boolean subscriptionExistsWithName(String projectId, String subscriptionId) throws IOException {
        SubscriptionName subscriptionName = SubscriptionName.create(projectId, subscriptionId);

        try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
            Subscription subscription = subscriptionAdminClient.getSubscription(subscriptionName);
            return true; // Subscription with this name exists, with no guarantees about what topics it is subscribed to

        } catch (NotFoundException e) {
            System.out.format("%s was thrown, assumed subscription does not exist, SubscriptionName: %s%n", e, subscriptionName);
            return false;
        } catch (IOException e) {
            System.out.format("Something went wrong while checking if subscription exists: %s was thrown.  SubscriptionName: %n", e, subscriptionName);
            throw e;
        } catch (Exception e) {
            String message = String.format("Something went wrong while checking if subscription exists: %s was thrown.  SubscriptionName: %n", e, subscriptionName);
            throw new RuntimeException(message, e);
        }
    }
}
