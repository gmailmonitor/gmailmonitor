package pack.persist.data;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Date;
import java.util.Objects;

@DatabaseTable(tableName = Schema.TABLE_HISTORY_EVENTS)
public class HistoryEvent {

    public static final String FIELD_ID = "id";
    public static final String FIELD_USER_ID = "user_id";
    public static final String FIELD_HISTORY_ID = "history_id";
    public static final String FIELD_DATE_OCCURRED = "date_occured";
    public static final String FIELD_STATUS_OBSERVED = "status_observed";
    public static final int FIELD_STATUS_OBSERVED__STATUS_OBSERVED_FIRSTUPDATE = 2; // It is believed this event occured before the user authenticated for the first time, no way to even guess when this happened
    public static final int FIELD_STATUS_OBSERVED__STATUS_OBSERVED_REALTIME = 1; // History event was recorded while system was running at steady-state (event probably observed in real-time)
    public static final int FIELD_STATUS_OBSERVED__STATUS_OBSERVED_DELAY = 0; // History event was recorded as system was starting up, (event possibly occurred while system was offline, now catching up)
    public static final String FIELD_JSON = "json";
    public static final String FIELD_MESSAGES_ADDED = "messages_added";
    public static final String FIELD_MESSAGES_REMOVED = "messages_removed";
    public static final String FIELD_UNREAD_ADDED = "unread_added";
    public static final String FIELD_UNREAD_REMOVED = "unread_removed";

    @DatabaseField(columnName = FIELD_ID, generatedId = true)
    private int id;

    @DatabaseField(columnName = FIELD_USER_ID)
    private Integer userId; // Application will default to "0" unless Integer is used

    @DatabaseField(columnName = FIELD_HISTORY_ID, canBeNull = false)
    private long historyId;

    @DatabaseField(columnName = FIELD_DATE_OCCURRED)
    private long dateOccurred;

    @DatabaseField(columnName = FIELD_STATUS_OBSERVED)
    private int statusObserved;

    @DatabaseField(columnName = FIELD_JSON, dataType = DataType.LONG_STRING, canBeNull = false)
    private String json;

    @DatabaseField(columnName = FIELD_MESSAGES_ADDED)
    private int messagesAdded;

    @DatabaseField(columnName = FIELD_MESSAGES_REMOVED)
    private int messagesRemoved;

    @DatabaseField(columnName = FIELD_UNREAD_ADDED)
    private int unreadAdded; // Refers to increasing the number of unread messages in the inbox

    @DatabaseField(columnName = FIELD_UNREAD_REMOVED)
    private int unreadRemoved; // Refers to decreasing the number of unread messages in the inbox


    @Override
    public int hashCode() {
        return Objects.hash(id, userId, historyId, dateOccurred, statusObserved, json, messagesAdded, messagesRemoved, unreadAdded, unreadRemoved);
    }

    @Override
    public boolean equals(Object other) { // Do not compare database primary key
        // Unclear if we depend on these at all
        throw new RuntimeException("Not Implemented");
    }

    @Override
    public String toString() {
        return "event historyId: " + historyId
                + " for user: " + userId
                + " messages +" + messagesAdded + "/-" + messagesRemoved
                + " unread +" + unreadAdded + "/-" + unreadRemoved
                + " observedStatus: " + statusObserved
                + " at " + (new Date(dateOccurred)).toString()
                ;
    }

    public boolean hasChanges() {
        return messagesAdded != 0 || messagesRemoved !=  0 || unreadAdded != 0 || unreadRemoved != 0;
    }

    //////////////////////////////////


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getHistoryId() {
        return historyId;
    }

    public void setHistoryId(long historyId) {
        this.historyId = historyId;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public long getDateOccurred() {
        return dateOccurred;
    }

    public void setDateOccurred(long dateOccurred) {
        this.dateOccurred = dateOccurred;
    }

    public int getStatusObserved() {
        return statusObserved;
    }

    public void setStatusObserved(int statusObserved) {
        this.statusObserved = statusObserved;
    }

    public int getMessagesAdded() {
        return messagesAdded;
    }

    public void setMessagesAdded(int messagesAdded) {
        this.messagesAdded = messagesAdded;
    }

    public int getMessagesRemoved() {
        return messagesRemoved;
    }

    public void setMessagesRemoved(int messagesRemoved) {
        this.messagesRemoved = messagesRemoved;
    }

    public int getUnreadAdded() {
        return unreadAdded;
    }

    public void setUnreadAdded(int messagesUnread) {
        this.unreadAdded = messagesUnread;
    }

    public int getUnreadRemoved() {
        return unreadRemoved;
    }

    public void setUnreadRemoved(int messagesRead) {
        this.unreadRemoved = messagesRead;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }
}
