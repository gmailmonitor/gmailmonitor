package pack.service.google.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.pubsub.v1.PubsubMessage;
import com.j256.ormlite.dao.Dao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pack.persist.DaoOwner;
import pack.persist.data.HistoryEvent;
import pack.persist.data.User;
import pack.service.task.TaskService;
import pack.service.UserService;
import pack.service.google.pubsub.schema.PubSubMessageData;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


// Functional interface containing callbacks for messages received
@Component
public class MessageReceiverPersistData implements MessageReceiver {

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());

    @Autowired private DaoOwner daoOwner;
    @Autowired private TaskService taskService;
    @Autowired private UserService userService;

    private long asyncPullTimeStarted;

    // Time, in ms, to catch up on receiving notifications that were queued while the application was not running
    // After this amount of time passes, any push notifications received will be recorded as having been logged in real-time
    public static final String SETTING_REALTIME_OBSERVATION_CATCH_UP_TIME_MS = "10000";
    private long settingRealtimeObservationCatchUpTimeMs = 0;

    @PostConstruct
    public void convertSettings() {
        // Fail-fast mechanism fails on startup if a setting is not readable
        settingRealtimeObservationCatchUpTimeMs = Long.parseLong(SETTING_REALTIME_OBSERVATION_CATCH_UP_TIME_MS);
        log.info("Using realtime-observation catch-up time of " + settingRealtimeObservationCatchUpTimeMs + " ms");
    }


    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
        Dao<HistoryEvent, String> historyDao = daoOwner.getHistoryDao(); // TODO move to postConstruct

        Map<String, String> attributesMap = message.getAttributesMap();
        // Expect attributes to be empty
        if (attributesMap.size() > 0) {
            log.info("Unexpected - Attributes map is not empty: " + attributesMap.toString());
        }

        PubSubMessageData pubSubMessageData;
        try {
            pubSubMessageData = bindFromMessageJson(message);
        } catch (IOException e) {
            log.info("Something went wrong trying to parse this message: " + message.toString()); // Exception is most likely fatal, OK to print the entire message
            e.printStackTrace();
            consumer.nack();
            return;
        }

        // Create google user if none exists
        String googleUserId = pubSubMessageData.getEmailAddress();
        User userForThisMessage;
        try {
            userForThisMessage = userService.getUserWithGoogleUserId(googleUserId);
            if (userForThisMessage == null) {
                log.error("Could not find user with google Id: " + googleUserId + " creating new user");
                userService.addUserWithGoogleId(googleUserId);
                userService.logAllUsers();
                userForThisMessage = userService.getUserWithGoogleUserId(googleUserId);
            }
        } catch (SQLException e) {
            log.info("Something went wrong querying for an existing user: " + message.toString()); // Exception is most likely fatal, OK to print the entire message
            e.printStackTrace();
            consumer.nack();
            return;
        }

        // Unclear what this was once used for
        // Long firstHistoryUpdateForUser = userService.getFirstHistoryUpdateTimeForUser(userForThisMessage.getId());

        // Create new or update existing history event described within this message
        BigInteger historyIdBigInteger = new BigInteger(pubSubMessageData.getHistoryId());
        HashMap<String, Object> queryMap = new HashMap<>();
        queryMap.put(HistoryEvent.FIELD_HISTORY_ID, historyIdBigInteger);
        try {
            final List<HistoryEvent> alreadyPersistedHistoryEvents = historyDao.queryForFieldValues(queryMap);
            if (alreadyPersistedHistoryEvents.size() > 1) {
                log.info("Unexpected, more than one history event with this history id: " + historyIdBigInteger);
                consumer.nack();
                return;

            }

            if (alreadyPersistedHistoryEvents.size() == 1) {
                // In practice, this does not get used since push notification history ID's do not directly contain mailbox updates
                // They only notify the application to update its history events
                HistoryEvent historyEventToUpdate = alreadyPersistedHistoryEvents.get(0);
                long dateOccurredOld = historyEventToUpdate.getDateOccurred();
                long dateOccurredNew = System.currentTimeMillis();
                historyEventToUpdate.setDateOccurred(dateOccurredNew);
                historyEventToUpdate.setUserId(userForThisMessage.getId());
                // setRealTimeObservationStatus(historyEventToUpdate); // Should have been set when it was created...
                log.info("Updating history id " + historyIdBigInteger + " for user " + userForThisMessage.getGoogleUserId() + " date occurred from " + dateOccurredOld + " to " + dateOccurredNew);
                historyDao.update(historyEventToUpdate);

            } else {
                long dateOccurred = System.currentTimeMillis();
                HistoryEvent historyEventToAdd = new HistoryEvent();
                historyEventToAdd.setHistoryId(historyIdBigInteger.longValue());
                historyEventToAdd.setDateOccurred(dateOccurred);
                historyEventToAdd.setJson(""); // Database integrity requirement....
                historyEventToAdd.setUserId(userForThisMessage.getId());
                setRealTimeObservationStatus(historyEventToAdd);
                log.info("Creating history id " + historyIdBigInteger + " for user " + userForThisMessage.getGoogleUserId() + " date occurred " + dateOccurred);
                historyDao.create(historyEventToAdd);
            }

        } catch (SQLException e) {
            log.info("Something went wrong querying for an existing history id: " + message.toString()); // Exception is most likely fatal, OK to print the entire message
            e.printStackTrace();
            consumer.nack();
            return;
        }

        log.info("PubSub message " + message.getMessageId() + " for user " + googleUserId + " processed successfully, sending acknowledgement");
        taskService.setUpdateForUser(userForThisMessage.getId()); // Set taskService to only update for this user
        taskService.taskStartSoon(); // Trigger a mailbox update
        consumer.ack();
    }



    // Get JSON data from PubsubMessage and bind to data object
    private PubSubMessageData bindFromMessageJson(PubsubMessage message) throws IOException {
        String content = message.getData().toStringUtf8();

        ObjectMapper mapper = new ObjectMapper();
        PubSubMessageData pubSubMessageData = mapper.readValue(content, PubSubMessageData.class);

        // Expected that these are all of the fields
        Map<String, Object> otherProperties = pubSubMessageData.getOtherProperties();
        if (otherProperties.size() > 0) {
            log.info("Unexpected - PubSub message data contains unexpected fields: " + otherProperties.keySet());
        }

        // todo return JSON object here // dont remember why??? // todo next time mention why
        return pubSubMessageData;
    }


    private void setRealTimeObservationStatus(HistoryEvent historyEventToAdd) throws SQLException {
        Long firstHistoryUpdateTime = userService.getFirstHistoryUpdateTimeForUser(historyEventToAdd.getUserId());

        if (firstHistoryUpdateTime == null) {// This is the very first update for this account
            historyEventToAdd.setStatusObserved(HistoryEvent.FIELD_STATUS_OBSERVED__STATUS_OBSERVED_FIRSTUPDATE);
            log.info("Realtime Observation Status: " + HistoryEvent.FIELD_STATUS_OBSERVED__STATUS_OBSERVED_FIRSTUPDATE + ", firstHistoryUpdateTime: " + firstHistoryUpdateTime + ", asyncPullTimeStarted: " + asyncPullTimeStarted);
            return;
        }

        boolean updateTooCloseToFirstUpdate = isNotificationRealTime(firstHistoryUpdateTime) == false; // Event close to first update - probably happened before user authenticated for the first time
        if (updateTooCloseToFirstUpdate) {
            historyEventToAdd.setStatusObserved(HistoryEvent.FIELD_STATUS_OBSERVED__STATUS_OBSERVED_FIRSTUPDATE);
            log.info("Realtime Observation Status: " + HistoryEvent.FIELD_STATUS_OBSERVED__STATUS_OBSERVED_FIRSTUPDATE + ", firstHistoryUpdateTime: " + firstHistoryUpdateTime + ", asyncPullTimeStarted: " + asyncPullTimeStarted);
            return;
        }

        if (isNotificationRealTime(asyncPullTimeStarted)) {
            historyEventToAdd.setStatusObserved(HistoryEvent.FIELD_STATUS_OBSERVED__STATUS_OBSERVED_REALTIME);
            log.info("Realtime Observation Status: " + HistoryEvent.FIELD_STATUS_OBSERVED__STATUS_OBSERVED_REALTIME + ", firstHistoryUpdateTime: " + firstHistoryUpdateTime + ", asyncPullTimeStarted: " + asyncPullTimeStarted);
            return;
        } else {
            historyEventToAdd.setStatusObserved(HistoryEvent.FIELD_STATUS_OBSERVED__STATUS_OBSERVED_DELAY);
            log.info("Realtime Observation Status: " + HistoryEvent.FIELD_STATUS_OBSERVED__STATUS_OBSERVED_DELAY + ", firstHistoryUpdateTime: " + firstHistoryUpdateTime + ", asyncPullTimeStarted: " + asyncPullTimeStarted);
            return;
        }
    }

    // If the message is observed very shortly after an async pull is started, this may be a catch-up notification and not one that was received in realtime
    // Reliability of making this type of determination is questionable
    // Stopping on breakpoints will mess with this!
    public boolean isNotificationRealTime(long compareTime) {
        long msAfterCompareTime = System.currentTimeMillis() - compareTime;
        if (msAfterCompareTime >= settingRealtimeObservationCatchUpTimeMs) {
            return true;
        } else {
            return false;
        }
    }


    public void setAsyncPullTimeStarted(long asyncPullTimeStarted) {
        this.asyncPullTimeStarted = asyncPullTimeStarted;
    }
}
