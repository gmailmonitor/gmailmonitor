package pack.persist.data;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Arrays;

/**
 * Created by malex on 2/23/2016.
 */

@DatabaseTable(tableName = "schema")
public class Schema {

    public static final String FIELD_SCHEMA_NAME = "schemaName";
    public static final String FIELD_SCHEMA_VERSION = "schemaVersion";

    public static final String TABLE_GMAIL_MESSAGES = "gmailMessages";
    public static final String TABLE_HISTORY_EVENTS = "gmailHistoryEvents";
    public static final String TABLE_USERS = "users";
    public static final String TABLE_GMAIL_LABEL_UPDATE = "gmailLabels";

    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField(columnName = FIELD_SCHEMA_NAME, canBeNull = false)
    private String schemaName;

    @DatabaseField(columnName = FIELD_SCHEMA_VERSION, canBeNull = false)
    private Long schemaVersion;

    Schema() {
        // all persisted classes must define a no-arg constructor with at least package visibility
    }

    public Schema(String schemaName) {
        this.schemaName = schemaName;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{
                id,
                schemaName,
                schemaVersion,
        });
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !other.getClass().equals(getClass())) {
            return false;
        }
        return schemaName.equals(((Schema) other).schemaName)
                && schemaVersion.equals(((Schema) other).schemaVersion);
    }

    ////////////////////////////////////////////////


    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public Long getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Long schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public void incrementSchemaVersion() {
        setSchemaVersion(this.getSchemaVersion() + 1);
    }
}