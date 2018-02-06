package pack.persist;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.j256.ormlite.table.TableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pack.GmailApiLaunch;
import pack.persist.data.*;
import pack.persist.data.tableinit.GmailLabelUpdateTableInit;
import pack.persist.data.tableinit.GmailMessageTableInit;
import pack.persist.data.tableinit.HistoryEventTableInit;
import pack.persist.data.tableinit.UserTableInit;

import javax.annotation.PostConstruct;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;

/**
 * Created by User on 4/17/2017.
 */
@Component
public class SchemaUpdate {

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());


    private final static Long DATABASE_SCHEMA_FIRST_VERSION = 1L;


    // Second generic parameter appears to be wrong?? should match the type of ID field
    private Dao<GmailMessage, String> messageDao;
    private Dao<GmailLabelUpdate, String> labelDao;
    private Dao<Schema, String> schemaDao;
    private Dao<HistoryEvent, String> historyDao;

    @Autowired
    private DaoOwner daoOwner;
    private Dao<User, String> userDao;


    @PostConstruct
    public void databaseInit() throws SQLException { // Throwing on @PostConstruct method will cause application to exit
        final String appName = GmailApiLaunch.SCHEMA_APP_NAME;

        try {
            TableUtils.createTableIfNotExists(daoOwner.getConnectionSource(), Schema.class);

            schemaDao = daoOwner.getSchemaDao();
            labelDao = daoOwner.getLabelDao();
            messageDao = daoOwner.getMessageDao();
            historyDao = daoOwner.getHistoryDao();
            userDao = daoOwner.getUserDao();

            HashMap<String, Object> queryMap = new HashMap<>();
            queryMap.put(Schema.FIELD_SCHEMA_NAME, appName);
            final List<Schema> schemas = schemaDao.queryForFieldValues(queryMap);

            Schema schemaObject;
            if (schemas.size() == 0) {
                log.info("No schema registered for this application: " + appName + ", initializing new schema");
                TableUtils.createTableIfNotExists(daoOwner.getConnectionSource(), GmailLabelUpdateTableInit.class);
                TableUtils.createTableIfNotExists(daoOwner.getConnectionSource(), GmailMessageTableInit.class);
                Schema newSchema = new Schema(appName);
                newSchema.setSchemaVersion(DATABASE_SCHEMA_FIRST_VERSION);
                schemaObject = newSchema;
                schemaDao.create(newSchema);

            } else if (schemas.size() == 1) {
                schemaObject = schemas.get(0);

            } else {
                throw new RuntimeException("Unexpected number of matching schema versions: " + schemas.size());
            }

            log.info("Schema version for " + appName + " : " + schemaObject.getSchemaVersion());
            performSchemaUpdatesIfNeeded(appName, schemaObject);


        } catch (SQLException e) {
            log.info("Problem initializing database");
            throw e;
        }
    }

    // This plan to be able to update an old database of any version 'mostly' worked - one weakness is that there appears to be a way to create a table with no columns
    // When existing persistence classes are modified, that modified class can not be used to re-create an earlier version of the database.
    // The solution going forward is to use a 'dummy' persistence object to create a table with one or zero columns.
    // Afterward, the dummy is never used again and the table can be modified using the DAO object to populate additional columns

    private void performSchemaUpdatesIfNeeded(String appName, Schema schemaObject) throws SQLException {
        if (schemaObject.getSchemaVersion() == 1) {
            messageDao.executeRaw("ALTER TABLE `" + Schema.TABLE_GMAIL_MESSAGES + "` ADD COLUMN " + GmailMessage.FIELD_INTERNAL_DATE + " BIGINT;");
            schemaObject.incrementSchemaVersion();
            schemaDao.update(schemaObject);
            log.info("Upgraded schema for " + appName + " to version " + schemaObject.getSchemaVersion());
        }

        if (schemaObject.getSchemaVersion() == 2) {
            messageDao.executeRaw("ALTER TABLE `" + Schema.TABLE_GMAIL_MESSAGES + "` ADD COLUMN " + GmailMessage.FIELD_HISTORY_ID + " BIGINT;");
            schemaObject.incrementSchemaVersion();
            schemaDao.update(schemaObject);
            log.info("Upgraded schema for " + appName + " to version " + schemaObject.getSchemaVersion());
        }

        if (schemaObject.getSchemaVersion() == 3) {
            // Add HistoryEvent table
            // Unfortunately, this upgrade action is not "replayable" since the HistoryEvent class has been modified.
            // Fortunately, if attempted, it will generate an informative stack trace which points here.
            TableUtils.createTableIfNotExists(daoOwner.getConnectionSource(), HistoryEventTableInit.class);
            schemaObject.incrementSchemaVersion();
            schemaDao.update(schemaObject);
            log.info("Upgraded schema for " + appName + " to version " + schemaObject.getSchemaVersion());
        }

        if (schemaObject.getSchemaVersion() == 4) {
            // Expand HistoryEvent table
            historyDao.executeRaw("ALTER TABLE `" + Schema.TABLE_HISTORY_EVENTS + "` ADD COLUMN " + HistoryEventTableInit.FIELD_DATE_OCCURRED + " BIGINT AFTER " + HistoryEventTableInit.FIELD_HISTORY_ID + ";");
            historyDao.executeRaw("ALTER TABLE `" + Schema.TABLE_HISTORY_EVENTS + "` ADD COLUMN " + HistoryEventTableInit.FIELD_STATUS_OBSERVED + " INT AFTER " + HistoryEventTableInit.FIELD_DATE_OCCURRED + ";");
            historyDao.executeRaw("ALTER TABLE `" + Schema.TABLE_HISTORY_EVENTS + "` ADD COLUMN " + HistoryEventTableInit.FIELD_MESSAGES_ADDED + " INT AFTER " + HistoryEvent.FIELD_JSON + ";");
            historyDao.executeRaw("ALTER TABLE `" + Schema.TABLE_HISTORY_EVENTS + "` ADD COLUMN " + HistoryEventTableInit.FIELD_MESSAGES_REMOVED + " INT AFTER " + HistoryEventTableInit.FIELD_MESSAGES_ADDED + ";");
            historyDao.executeRaw("ALTER TABLE `" + Schema.TABLE_HISTORY_EVENTS + "` ADD COLUMN " + HistoryEventTableInit.FIELD_UNREAD_ADDED + " INT AFTER " + HistoryEventTableInit.FIELD_MESSAGES_REMOVED + ";");
            historyDao.executeRaw("ALTER TABLE `" + Schema.TABLE_HISTORY_EVENTS + "` ADD COLUMN " + HistoryEventTableInit.FIELD_UNREAD_REMOVED + " INT AFTER " + HistoryEventTableInit.FIELD_UNREAD_ADDED + ";");
            schemaObject.incrementSchemaVersion();
            schemaDao.update(schemaObject);
            log.info("Upgraded schema for " + appName + " to version " + schemaObject.getSchemaVersion());
        }

        if (schemaObject.getSchemaVersion() == 5) {
            // Expand HistoryEvent table
            historyDao.executeRaw("ALTER TABLE `" + Schema.TABLE_HISTORY_EVENTS + "` ALTER COLUMN historyId RENAME TO " + HistoryEvent.FIELD_HISTORY_ID + ";");
            historyDao.executeRaw("ALTER TABLE `" + Schema.TABLE_HISTORY_EVENTS + "` ALTER COLUMN " + HistoryEventTableInit.FIELD_DATE_OCCURRED + " RENAME TO " + HistoryEvent.FIELD_DATE_OCCURRED + ";");
            historyDao.executeRaw("ALTER TABLE `" + Schema.TABLE_HISTORY_EVENTS + "` ALTER COLUMN " + HistoryEventTableInit.FIELD_STATUS_OBSERVED + " RENAME TO " + HistoryEvent.FIELD_STATUS_OBSERVED + ";");
            historyDao.executeRaw("ALTER TABLE `" + Schema.TABLE_HISTORY_EVENTS + "` ALTER COLUMN " + HistoryEventTableInit.FIELD_MESSAGES_ADDED + " RENAME TO " + HistoryEvent.FIELD_MESSAGES_ADDED + ";");
            historyDao.executeRaw("ALTER TABLE `" + Schema.TABLE_HISTORY_EVENTS + "` ALTER COLUMN " + HistoryEventTableInit.FIELD_MESSAGES_REMOVED + " RENAME TO " + HistoryEvent.FIELD_MESSAGES_REMOVED + ";");
            historyDao.executeRaw("ALTER TABLE `" + Schema.TABLE_HISTORY_EVENTS + "` ALTER COLUMN " + HistoryEventTableInit.FIELD_UNREAD_ADDED + " RENAME TO " + HistoryEvent.FIELD_UNREAD_ADDED + ";");
            historyDao.executeRaw("ALTER TABLE `" + Schema.TABLE_HISTORY_EVENTS + "` ALTER COLUMN " + HistoryEventTableInit.FIELD_UNREAD_REMOVED + " RENAME TO " + HistoryEvent.FIELD_UNREAD_REMOVED + ";");

            schemaObject.incrementSchemaVersion();
            schemaDao.update(schemaObject);
            log.info("Upgraded schema for " + appName + " to version " + schemaObject.getSchemaVersion());
        }

        if (schemaObject.getSchemaVersion() == 6) {
            // Create user table
            TableUtils.createTable(daoOwner.getConnectionSource(), UserTableInit.class);
            userDao.executeRaw("ALTER TABLE `" + Schema.TABLE_USERS + "` ADD COLUMN " + User.FIELD_GOOGLE_USER_ID + " VARCHAR " + ";");
            userDao.executeRaw("ALTER TABLE `" + Schema.TABLE_USERS + "` ADD CONSTRAINT unique_" + User.FIELD_GOOGLE_USER_ID + " UNIQUE(" + User.FIELD_GOOGLE_USER_ID + ")" + ";");

            // Create a new user to attribute all records to
            String googleUserId = "user@unknown.com";
            log.info("Creating a new user to attribute existing assets to, placeholder Google user ID: " + googleUserId);

            // Add user to database.  There should be no other users.
            User newUser = new User();
            newUser.setGoogleUserId(googleUserId);
            userDao.create(newUser);
            int generatedUserId = newUser.getId();

            // Add user ID column to Gmail label updates table and set all records to the internal ID of the user that was just created.  Then add NOT NULL constraint
            labelDao.executeRaw("ALTER TABLE `" + Schema.TABLE_GMAIL_LABEL_UPDATE + "` ADD COLUMN " + GmailLabelUpdate.FIELD_USER_ID + " BIGINT AFTER " + GmailLabelUpdate.FIELD_ID + ";");
            UpdateBuilder<GmailLabelUpdate, String> labelUpdateBuilder = labelDao.updateBuilder();
            labelUpdateBuilder.updateColumnValue(GmailLabelUpdate.FIELD_USER_ID, generatedUserId);
            labelUpdateBuilder.update();
            labelDao.executeRaw("ALTER TABLE `" + Schema.TABLE_GMAIL_LABEL_UPDATE + "` ALTER " + GmailLabelUpdate.FIELD_USER_ID + " SET NOT NULL " + ";");

            // Add user ID column to Gmail Messages table and set all records to the internal ID of the user that was just created.  Then add NOT NULL constraint
            labelDao.executeRaw("ALTER TABLE `" + Schema.TABLE_GMAIL_MESSAGES + "` ADD COLUMN " + GmailMessage.FIELD_USER_ID + " BIGINT AFTER " + GmailMessage.FIELD_ID + ";");
            UpdateBuilder<GmailMessage, String> messageUpdateBuilder = messageDao.updateBuilder();
            messageUpdateBuilder.updateColumnValue(GmailMessage.FIELD_USER_ID, generatedUserId);
            messageUpdateBuilder.update();
            labelDao.executeRaw("ALTER TABLE `" + Schema.TABLE_GMAIL_MESSAGES + "` ALTER " + GmailMessage.FIELD_USER_ID + " SET NOT NULL " + ";");

            // Add user ID column to History Event table and set all records to the internal ID of the user that was just created.  Then add NOT NULL constraint
            labelDao.executeRaw("ALTER TABLE `" + Schema.TABLE_HISTORY_EVENTS + "` ADD COLUMN " + HistoryEvent.FIELD_USER_ID + " BIGINT AFTER " + HistoryEvent.FIELD_ID + ";");
            UpdateBuilder<HistoryEvent, String> historyUpdateBuilder = historyDao.updateBuilder();
            historyUpdateBuilder.updateColumnValue(HistoryEvent.FIELD_USER_ID, generatedUserId);
            messageUpdateBuilder.update();
            labelDao.executeRaw("ALTER TABLE `" + Schema.TABLE_HISTORY_EVENTS + "` ALTER " + GmailMessage.FIELD_USER_ID + " SET NOT NULL " + ";");

            schemaObject.incrementSchemaVersion();
            schemaDao.update(schemaObject);
            log.info("Upgraded schema for " + appName + " to version " + schemaObject.getSchemaVersion());
        }

    }
}
