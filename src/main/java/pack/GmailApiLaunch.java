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

    //TODO testing
    @Autowired private ApplicationConfiguration applicationConfiguration;

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



        // TODO remove keys and secrets for posting on github

        // TODO test recover from de-auth app token

        // TODO display on UI
        // TODO support a few pre-selected time ranges

        // TODO see if permission set can be reduced to Labels only
        // TODO upload to GitHub again

        // TODO 401 Unauthorized on startup tasks should not prevent the application from starting


        // TODO UI
        // Use mailbox updates to obtain more granular unread/total counts
        // Add high/low marks to stats
        // Consider adding @JsonIgnore annotation to mixin

        // todo remaining http endpoints should be REST


        // TODO stop using System.out, user logger instead
        // TODO implement multiple user accounts
        // calculate messages added/removed over variable time granularity
        // obtain message counts for specified time granularity


        // TODO remove TODO's
        // Split GmailService into operations which access the Gmail API vs operations which access the database.  Operations needing both should be run by a higher class?
        // TODO table for application settings (columns: schemaname, settingname, settingvalue)

        // TODO system generates multiple changes for the same date/time



//        gmailService.printLargestSenderInfo(6); // Prepares and displays statistics
//        GmailApiService.getMessageDetailsById("7209405991688");

//        printLabelUpdateDiagnostics();

        // Get a single History ID, for debugging purposes only

        // code to be tested on startup would go here

        // List<History> historyOne = gmailService.getHistoryFrom(6440066 + "");
        // log.info(historyOne);


        // specializedTasks();

        // TODO hold off on doing any of this while mult-user is being implemented
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
