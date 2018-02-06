package pack.persist.data;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Date;
import java.util.Objects;

/**
 * Created by User on 1/3/2016.
 */

@DatabaseTable(tableName = Schema.TABLE_GMAIL_MESSAGES)
public class GmailMessage {

    public static final String FIELD_ID = "id";
    public static final String FIELD_USER_ID = "user_id";
    public static final String FIELD_MESSAGE_ID = "messageId";
    public static final String FIELD_HISTORY_ID = "historyId";
    public static final String FIELD_THREAD_ID = "threadId";
    public static final String FIELD_HEADER_FROM = "headerFrom";
    public static final String FIELD_INTERNAL_DATE = "internalDate";


    @DatabaseField(columnName = FIELD_ID, generatedId = true)
    private int id;

    @DatabaseField(columnName = FIELD_USER_ID)
    private Integer userId; // Application will default to "0" unless Integer is used

    // MessageId and ThreadId always provided in Summary view
    @DatabaseField(columnName = FIELD_MESSAGE_ID, canBeNull = false)
    private String messageId;

    // MessageId and ThreadId always provided in Summary view
    @DatabaseField(columnName = FIELD_THREAD_ID, canBeNull = false)
    private String threadId;

    @DatabaseField(columnName = FIELD_HEADER_FROM, canBeNull = true)
    private String headerFrom;

    @DatabaseField(columnName = FIELD_INTERNAL_DATE, canBeNull = true)
    private Long internalDate;

    @DatabaseField(columnName = FIELD_HISTORY_ID, canBeNull = true)
    private Long historyId;


    GmailMessage() {
        // ORMLite requirement: all persisted classes must define a no-arg constructor with at least package visibility
    }

    public GmailMessage(String messageId, String threadId) {
        this.messageId = messageId;
        this.threadId = threadId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId, messageId, threadId, headerFrom, internalDate, historyId);
    }

    @Override
    public String toString() {
        return "userId: " + userId
                + " headerFrom: " + headerFrom
                + " historyId: " + historyId
                + " messageId: " + messageId
                + " internalDate: " + (new Date(internalDate)).toString()
                ;
    }

    @Override
    public boolean equals(Object other) { // Do not compare database primary key
        // Unclear if these still work
        throw new RuntimeException("Not Implemented");
        // if (other == null || !other.getClass().equals(getClass())) {
        //     return false;
        // }
        // return messageId.equals(((GmailMessage) other).messageId)
        //         && historyId.equals(((GmailMessage) other).historyId)
        //         && threadId.equals(((GmailMessage) other).threadId)
        //         && headerFrom.equals(((GmailMessage) other).headerFrom)
        //         && internalDate.equals(((GmailMessage) other).internalDate);
    }

    //////////////////////////////////////////////////

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getHeaderFrom() {
        return headerFrom;
    }

    public void setHeaderFrom(String headerFrom) {
        this.headerFrom = headerFrom;
    }

    public void setInternalDate(Long internalDate) {
        this.internalDate = internalDate;
    }

    public Long getInternalDate() {
        return internalDate;
    }

    public Long getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Long historyId) {
        this.historyId = historyId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }
}
