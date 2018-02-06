package pack.persist.data.tableinit;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import pack.persist.data.GmailLabelUpdate;
import pack.persist.data.Schema;

import java.util.Date;
import java.util.Objects;

@DatabaseTable(tableName = Schema.TABLE_GMAIL_LABEL_UPDATE)
public class GmailLabelUpdateTableInit {


    @DatabaseField(columnName = GmailLabelUpdate.FIELD_ID, generatedId = true)
    private int id;

    @DatabaseField(columnName = GmailLabelUpdate.FIELD_LABEL_NAME, canBeNull = false)
    private String labelName;

    @DatabaseField(columnName = GmailLabelUpdate.FIELD_LAST_HISTORY_ID, canBeNull = false)
    private long lastHistoryId;

    @DatabaseField(columnName = GmailLabelUpdate.FIELD_UPDATE_TIME_MILLIS, canBeNull = false)
    private long updateTimeMillis;

    @DatabaseField(columnName = GmailLabelUpdate.FIELD_MESSAGES_TOTAL, canBeNull = false)
    private int messagesTotal;

    @DatabaseField(columnName = GmailLabelUpdate.FIELD_MESSAGES_UNREAD, canBeNull = false)
    private int messagesUnread;

    @DatabaseField(columnName = GmailLabelUpdate.FIELD_THREADS_TOTAL, canBeNull = false)
    private int threadsTotal;

    @DatabaseField(columnName = GmailLabelUpdate.FIELD_THREADS_UNREAD, canBeNull = false)
    private int threadsUnread;
}
