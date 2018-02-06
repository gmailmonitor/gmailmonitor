package pack.service.google.pubsub;// Imports the Google Cloud client library


import com.google.cloud.pubsub.v1.PagedResponseWrappers;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.pubsub.v1.ListTopicsRequest;
import com.google.pubsub.v1.ProjectName;
import com.google.pubsub.v1.Topic;
import com.google.pubsub.v1.TopicName;

public class TopicService {

    public static void createTopic(String projectId, String topicId) {

        // Create a new topic
        TopicName topic = TopicName.of(projectId, topicId);
        try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
            topicAdminClient.createTopic(topic);
        } catch (Exception e) {
            RuntimeException runtimeException = convertGoogleExceptionToRuntimeException(e);
            throw runtimeException;
        }

        System.out.printf("Topic %s:%s created.\n", topic.getProject(), topic.getTopic());
    }

    private static RuntimeException convertGoogleExceptionToRuntimeException(Exception e) {
        String message = "Exception was thrown, possibly from google's AutoCloseable object";
        // Rethrowing as RuntimeException means every caller does not need to throw Exception
        RuntimeException runtimeException = new RuntimeException(message, e);
        return runtimeException;
    }

    public static boolean topicExists(String projectId, String topicId) {
        TopicName topic = TopicName.of(projectId, topicId);
        Iterable<Topic> topics = getTopics(projectId);
        for (Topic nextTopic : topics) {
            // nextTopic.toString() returns a JSON object, use getName()
            if (nextTopic.getName().equals(topic.toString())) {
                return true;
            }
        }
        return false;
    }


    // https://cloud.google.com/pubsub/docs/admin#list_topics
    public static Iterable<Topic> getTopics(String projectId) {
        try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
            ListTopicsRequest.Builder builder = ListTopicsRequest.newBuilder();
            ProjectName of = ProjectName.of(projectId);
            ListTopicsRequest.Builder builder1 = builder.setProjectWithProjectName(of);
            ListTopicsRequest listTopicsRequest = builder1.build();
            PagedResponseWrappers.ListTopicsPagedResponse response = topicAdminClient.listTopics(listTopicsRequest);
            Iterable<Topic> topics = response.iterateAll();
            return topics;
        } catch (Exception e) {
            RuntimeException runtimeException = convertGoogleExceptionToRuntimeException(e);
            throw runtimeException;
        }
    }


    public static void deleteTopic(String projectId, String topicId) {
        try {
            try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
                TopicName topicName = TopicName.of(projectId, topicId);
                topicAdminClient.deleteTopic(topicName);
            }
        } catch (Exception e) {
            RuntimeException runtimeException = convertGoogleExceptionToRuntimeException(e);
            throw runtimeException;
        }
    }
}