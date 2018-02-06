package pack.service.task;

import com.j256.ormlite.dao.Dao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import pack.persist.DaoOwner;
import pack.persist.data.User;
import pack.service.UserService;
import pack.service.google.gmail.GmailApiService;
import pack.service.google.gmail.GmailDataService;
import pack.service.google.gmail.GmailService;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by User on 11/13/2017.
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());

    public static final int SECONDS_BETWEEN_TASKS = 60 * 15; // TODO consider externalizing

    @Autowired private GmailService gmailService;
    @Autowired private GmailApiService gmailApiService;
    @Autowired private GmailDataService gmailDataService;
    @Autowired private UserService userService;
    @Autowired private ThreadPoolTaskScheduler threadPoolTaskScheduler;
    @Autowired private DaoOwner daoOwner;

    private ScheduledFuture scheduledFuture;
    private Integer nextUpdateForUser;

    public ScheduledFuture getScheduledFuture() {
        return scheduledFuture;
    }

    // Return true if we are certain that no task is scheduled to run.
    public synchronized boolean taskCancel() {
        boolean internalValue = taskCancelInternal();
        log.warn("There are " + threadPoolTaskScheduler.getActiveCount() + " active threads");
        return internalValue;
    }

    private boolean taskCancelInternal() {
        int delayThresholdMs = 500; // Don't cancel task if it is about to execute in this many ms

        if (scheduledFuture == null) {
            // No task was ever scheduled
            log.info("Cannot cancel, task not scheduled (considered successful)");
            return true;
        }

        // Task was scheduled, determine if it is still waiting to start
        long delayUntilExecution = scheduledFuture.getDelay(TimeUnit.MILLISECONDS); // Code below assumes time in ms
        if (delayUntilExecution > delayThresholdMs) {
            log.info("Attempting to cancel scheduled task starting in " + delayUntilExecution + "ms");
            scheduledFuture.cancel(false);

        } else {
            //TODO convert to trace log
            String message = String.format("Timer delay of %s too close to threshold value of %s or task has already started, will not attempt to cancel", delayUntilExecution, delayThresholdMs);
            log.info(message);
        }

        //TODO convert to trace log
        boolean cancelled = scheduledFuture.isCancelled();
        boolean done = scheduledFuture.isDone(); // Even if cancelling is not possible, task may have already finished (depending on circumstances and re-scheduling strategy)
        String message = String.format("%sms remaining until task start, cancelled: %s, done: %s", delayUntilExecution, cancelled, done);
        log.info(message);

        if (cancelled || done) {
            // Considered successful if we cancelled the task before it started, or it has already run to completion
            log.info("Task was successfully cancelled (or has already finished)");
            return true;
        }

        log.info("Unable to guarantee that no tasks are scheduled");
        return false;
    }

    // Cancel any already-scheduled task and create a new one set to start at a pre-determined delay
    // Return true if task delay was reset successfully
    public synchronized boolean taskResetTimer() {
        return resetTimer(SECONDS_BETWEEN_TASKS);
    }

    // Cancel any already-scheduled task and create a new one set to start (almost) immediately
    // Return true if task delay was reset successfully
    // Useful for when a push notification is received, however these may be received in parallel, so a short delay is useful
    // Allows the start-time to be pushed back multiple times as multiple push notifications are processed
    public synchronized boolean taskStartSoon() {
        return resetTimer(3);
    }

    private synchronized boolean resetTimer(int secondsDelay) {
        boolean cancelResult = taskCancel();
        if (cancelResult == false) {
            log.info("Could not cancel already-scheduled task, it will not be re-scheduled");
            return false;
        }

        schedule(secondsDelay);
        return true;
    }

    // Does not check to see if other tasks are cancelled
    private void schedule(int secondsDelay) {
        log.info("About to schedule task");
        RunnableTask task = new RunnableTask(daoOwner.getUserDao(), nextUpdateForUser, "execute-in-" + secondsDelay + "-seconds");
        clearUpdateForUser();
        Date startTime = new Date(System.currentTimeMillis() + secondsDelay * 1000);
        scheduledFuture = threadPoolTaskScheduler.schedule(task, startTime);
        log.warn("There are " + threadPoolTaskScheduler.getActiveCount() + " active threads");
    }

    public void setUpdateForUser(Integer userIdLoggedIn) {
        nextUpdateForUser = userIdLoggedIn;
    }

    private void clearUpdateForUser() {
        nextUpdateForUser = null;
    }

    class RunnableTask implements Runnable {

        private final String message;
        private final Dao<User, String> userDao;
        private final Integer nextUpdateForUser;


        public RunnableTask(Dao<User, String> userDao, Integer nextUpdateForUser, String message){
            this.nextUpdateForUser = nextUpdateForUser;
            this.message = message;
            this.userDao = userDao;
        }

        @Override
        public void run() {
            if (nextUpdateForUser == null) {
                executeUpdateForAllUsers();
            } else {
                User userWithId = null;
                try {
                    userWithId = userService.getUserWithId(nextUpdateForUser);
                    executeUpdateForSingleUser(userWithId);
                } catch (SQLException | IOException e) {
                    throw new RuntimeException("Exception processing update tasks for user: " + userWithId, e);
                }
            }
        }

        // Perform updates for all users through iteration
        private void executeUpdateForAllUsers() {
            List<User> users = null;
            String lastUserProcessed = null;
            try {
                users = userDao.queryForAll();

                for (User nextUser : users) {
                    lastUserProcessed = nextUser.getGoogleUserId();
                    executeUpdateForSingleUser(nextUser);
                }
            } catch (SQLException | IOException e) {
                throw new RuntimeException("Exception processing update tasks for user: " + lastUserProcessed, e);
            }
        }

        // Performs updates for a single user
        private void executeUpdateForSingleUser(User user) throws IOException {
            boolean userCredentialValid = gmailApiService.isCredentialValid(user.getGoogleUserId());
            if (userCredentialValid) {
                log.info("Credential is valid, executing update tasks for user: " + user.getGoogleUserId());
                // This is a non-interactive process,
                // if our credential is invalid there is no way to re-obtain authorization until the user is present at the UI
                performApiUpdateTasksForUser(user);

                log.info("Finished Runnable Task: " + message + " on thread " + Thread.currentThread().getName() + " -  Scheduling new task to start in {} seconds", SECONDS_BETWEEN_TASKS);
                schedule(SECONDS_BETWEEN_TASKS);


            } else {
                log.info("Credential is invalid, skipping non-interactive update tasks for user: " + user.getGoogleUserId());
            }
        }

        // Things to run periodically
        private void performApiUpdateTasksForUser(User user) {
            log.info(new Date() + " Started Runnable Task: " + message + " on thread " + Thread.currentThread().getName());

            String googleUserId = user.getGoogleUserId();

            try {
                log.info("Executing tasks for google user: " + googleUserId);
                Long lastExaminedHistoryIdFromLabelInfo = gmailDataService.getLastExaminedHistoryIdFromLabelInfo(googleUserId);
                Long lastExaminedHistoryIdFromHistory = gmailDataService.getLastExaminedHistoryIdFromHistory(googleUserId);

                log.info(" -- HISTORY -- User: " + googleUserId + " last examined history id: " + lastExaminedHistoryIdFromLabelInfo + " (label info), " + lastExaminedHistoryIdFromHistory + " (history info) ");

                Integer softMaximumMessages;
                if (lastExaminedHistoryIdFromLabelInfo == 0 && lastExaminedHistoryIdFromHistory == 0) {
                    softMaximumMessages = 50;
                    log.info(" -- HISTORY -- New Mailbox - examining a maximum of " + softMaximumMessages + " messages");

                } else {
                    //We know the last-examined history ID, so catch up on history events before updating total count
                    Long nextValidHistoryId = gmailService.findNextValidHistoryId(user, lastExaminedHistoryIdFromHistory +"");
                    log.info(" -- HISTORY -- Examining records starting from history id: " + nextValidHistoryId);
                    gmailService.updateHistoryStartingWith(user.getId(), nextValidHistoryId);

                    softMaximumMessages = null; // Will fetch message details for ALL new messages
                }

                gmailService.resyncInboxNewMessagesUpTo(user, softMaximumMessages); // Update stats and extra info on labels and messages

            } catch (IOException | SQLException e) {
                log.info(e.getClass().getSimpleName() + " was thrown");
                e.printStackTrace();
            }
        }
    }
}
