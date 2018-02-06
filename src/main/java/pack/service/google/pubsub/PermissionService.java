package pack.service.google.pubsub;

import com.google.cloud.Identity;
import com.google.cloud.Role;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.iam.v1.Binding;
import com.google.iam.v1.Policy;
import com.google.iam.v1.TestIamPermissionsResponse;
import com.google.protobuf.ProtocolStringList;
import com.google.pubsub.v1.TopicName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by User on 10/13/2017.
 */
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());

    public static void addRole(String projectId, String topicId, String roleName, String serviceAccountEmail) {
        try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
            String topicName = TopicName.create(projectId, topicId).toString();

            // add role -> members binding
            Role roleToAdd = Role.of(roleName);
            Identity serviceAccountGmailApi = Identity.serviceAccount(serviceAccountEmail);
            Binding binding =
                    Binding.newBuilder()
                            .setRole(roleToAdd.toString())
                            .addMembers(serviceAccountGmailApi.toString())
                            .build();
            // create updated policy // the resource is the topic name
            Policy existingPolicy = topicAdminClient.getIamPolicy(topicName);

            Policy updatedPolicy = Policy.newBuilder(existingPolicy).addBindings(binding).build();

            log.info("Built update policy policy: ");
            describePolicy(updatedPolicy);

            updatedPolicy = topicAdminClient.setIamPolicy(topicName, updatedPolicy);

            log.info("Updated bindings for policy: ");
            describePolicy(updatedPolicy);
        } catch (IOException e) {
            throw new RuntimeException("Exception thrown while adding permission/role", e);
        } catch (Exception e) {
            throw new RuntimeException("Exception thrown while adding permission/role", e);
        }
    }

    public static void setEmptyPolicy(String projectId, String topicId) throws Exception {
        try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
            String topicName = TopicName.of(projectId, topicId).toString();
            Policy updatedPolicy = Policy.newBuilder().build();

            log.info("Built update policy policy: ");
            describePolicy(updatedPolicy);

            updatedPolicy = topicAdminClient.setIamPolicy(topicName, updatedPolicy);

            log.info("Updated bindings for policy: ");
            describePolicy(updatedPolicy);
        }
    }

    private static void describePolicy(Policy updatedPolicy) {
        for (Binding nextBinding : updatedPolicy.getBindingsList()) {
            String role = nextBinding.getRole();
            String memberNames = "";
            for (String nextMemberName : nextBinding.getMembersList()) {
                if (memberNames.length() > 0) {
                    memberNames += ", ";
                }
                memberNames += nextMemberName;
            }

            log.info("Role: " + role + ", Members: " + memberNames);
        }
    }

    public static boolean checkRole(String projectId, String topicId, String roleToCheck, String permissionHolderToCheck) throws IOException {
        try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
            // projects.topics.publish identifies a method, corresponding permission is pubsub.topics.publish
            ArrayList<String> permissionsToCheck = new ArrayList<>();
            permissionsToCheck.add(roleToCheck);

            String resource = TopicName.of(projectId, topicId).toString();
            Policy iamPolicy = topicAdminClient.getIamPolicy(resource);

            // Must check both role and member
            List<Binding> bindingsList = iamPolicy.getBindingsList();
            for (Binding nextBinding : bindingsList) {
                String role = nextBinding.getRole();
                if (role.equals(roleToCheck) == false) {
                    continue;  // Not the role we are interested in, skip to next binding
                }

                ProtocolStringList membersList = nextBinding.getMembersList();
                if (membersList.contains(permissionHolderToCheck)) {
                    return true;
                }
            }

            return false;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Google auto-closeable threw Exception", e);
        }
    }

    // This is for checking if this entity (tokens associated with this application) has the specified permission
    // Not for checking if others, e.g. gmail service account, has permissions
    // Calling this on a project we already hold the owner token for will not be very revealing
    public static boolean checkPermissionUsingtestIamPermissions(String projectId, String topicId, String permissionToCheck) throws Exception {
        try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
            String resource = TopicName.of(projectId, topicId).toString();

            // projects.topics.publish identifies a method, corresponding permission is pubsub.topics.publish
            ArrayList<String> permissionsToCheck = new ArrayList<>();
            permissionsToCheck.add(permissionToCheck);

            // roles != permissions // roles contain one or more permissions  // https://cloud.google.com/pubsub/docs/access_control
            // Resource identifier format described here:  // https://cloud.google.com/pubsub/docs/overview#names
            TestIamPermissionsResponse testIamPermissionsResponse = topicAdminClient.testIamPermissions(resource, permissionsToCheck);
            log.info(testIamPermissionsResponse.toString());
            ProtocolStringList permissionsList = testIamPermissionsResponse.getPermissionsList();
            for (String nextPermission : permissionsList) {
                if (nextPermission.equals(permissionToCheck)) {
                    return true;
                }
            }
            return false;
        }
    }
}
