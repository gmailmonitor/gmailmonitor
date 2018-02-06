package pack.persist.data.tableinit;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import pack.persist.data.GmailMessage;
import pack.persist.data.Schema;

import java.util.Objects;

/**
 * Created by User on 1/3/2016.
 */

@DatabaseTable(tableName = Schema.TABLE_GMAIL_MESSAGES)
public class GmailMessageTableInit {

    @DatabaseField(columnName = GmailMessage.FIELD_ID, generatedId = true)
    private int id;

    @DatabaseField(columnName = GmailMessage.FIELD_MESSAGE_ID, canBeNull = false)
    private String messageId;

    @DatabaseField(columnName = GmailMessage.FIELD_THREAD_ID, canBeNull = false)
    private String threadId;

    @DatabaseField(columnName = GmailMessage.FIELD_HEADER_FROM)
    private String headerFrom;
}
