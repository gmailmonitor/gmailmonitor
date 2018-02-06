package pack.persist.data.tableinit;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import pack.persist.data.HistoryEvent;
import pack.persist.data.Schema;

import java.util.Objects;

@DatabaseTable(tableName = Schema.TABLE_HISTORY_EVENTS)
public class HistoryEventTableInit {

    public static final String FIELD_HISTORY_ID = "historyId";
    public static final String FIELD_DATE_OCCURRED = "dateOccurred";
    public static final String FIELD_STATUS_OBSERVED = "statusObserved";
    public static final String FIELD_MESSAGES_ADDED = "messagesAdded";
    public static final String FIELD_MESSAGES_REMOVED = "messagesRemoved";
    public static final String FIELD_UNREAD_ADDED = "messagesUnread";
    public static final String FIELD_UNREAD_REMOVED = "messagesRead";

    @DatabaseField(columnName = HistoryEvent.FIELD_ID, generatedId = true)
    private int id;

    @DatabaseField(columnName = FIELD_HISTORY_ID, canBeNull = false)
    private long historyId;

    @DatabaseField(columnName = HistoryEvent.FIELD_JSON, dataType = DataType.LONG_STRING, canBeNull = false)
    private String json;
    }
