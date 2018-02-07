package pack;

import com.j256.ormlite.dao.Dao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pack.persist.DaoOwner;
import pack.persist.SchemaUpdate;
import pack.persist.SpecializedDatabaseTasks;
import pack.persist.data.GmailLabelUpdate;
import pack.persist.data.User;
import pack.service.task.TaskService;
import pack.service.google.gmail.GmailApiService;
import pack.service.google.gmail.GmailDataService;
import pack.service.google.pubsub.PubSubService;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

/**
 * Created by User on 12/27/2015.
 */

@Component
public class GmailApiLaunch {

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());

    // Uncomment if implicit dependencies is needed
    // @Autowired private ApplicationConfiguration applicationConfiguration;

    public static final String SCHEMA_APP_NAME = "GMAIL_MAILBOX_STATS";

    // Auto-wiring ensures schema update is performed before other tasks are launched - even if reference is not used
    @Autowired private SchemaUpdate schemaUpdate;

    @Autowired private GmailDataService gmailDataService;
    @Autowired private GmailApiService gmailApiService;
    @Autowired private TaskService taskService;
    @Autowired private PubSubService pubSubService;
    @Autowired private SpecializedDatabaseTasks specializedDatabaseTasks;

    @Autowired private DaoOwner daoOwner;

    public static long startupTime = 0;

    @PostConstruct
    public void postConstruct() throws Exception {
        // code to be tested on startup would go here
        // specializedTasks();
        startupTasks();
    }

    private void specializedTasks() throws SQLException, IOException {
        // specializedDatabaseTasks.parseHistoryEventJsonForCountChanges();
        // specializedDatabaseTasks.parseHistoryEventJsonForMissingDateInfo();
        // specializedDatabaseTasks.rewriteDatesOnHistoryEventsWithChanges();
    }

    private void startupTasks() throws Exception {
        if (startupTime != 0) {
            throw new RuntimeException("Startup tasks were called more than once - tasks were already launched at " + new Date(startupTime));
        }

        startupTime = System.currentTimeMillis();
        taskService.taskResetTimer(); // Automatically schedule repeated task


        // Ensures that Creates topics and subscriptions are created as required by pub/sub functionality
        pubSubService.preparePrerequisites();

        List<User> allUsers = getAllUsers();
        for (User user : allUsers) {
            startupTaskPerUser(user);
        }

        pubSubService.startAsyncPull();
    }

    private void startupTaskPerUser(User user) throws Exception {
        String googleUserId = user.getGoogleUserId();
        boolean credentialValid = gmailApiService.isCredentialValid(googleUserId);
        if (credentialValid) {
            log.info("Credential valid for user: " + googleUserId + ", launching prerequisite tasks");
            pubSubService.watchMailbox(googleUserId);
        }
    }

    // Prints diagnostic info on label updates
    private void printLabelUpdateDiagnostics() throws SQLException {
        List<GmailLabelUpdate> labelUpdates = gmailDataService.getLabelUpdates();
        for (GmailLabelUpdate labelUpdate : labelUpdates) {
            String labelName = labelUpdate.getLabelName();
            long lastHistoryId = labelUpdate.getLastHistoryId();
            log.info("Label Update: " + labelUpdate + " last historyId: " + lastHistoryId);

        }
    }

    private List<User> getAllUsers() throws SQLException {
        Dao<User, String> userDao = daoOwner.getUserDao();
        List<User> users = userDao.queryForAll();
        return users;
    }


}
