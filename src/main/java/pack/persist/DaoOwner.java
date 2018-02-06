package pack.persist;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pack.ApplicationConfiguration;
import pack.persist.data.*;

import javax.annotation.PostConstruct;
import java.sql.SQLException;

/**
 * Created by malex on 7/7/2016.
 */

@Component
public class DaoOwner {

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());

    @Autowired ApplicationConfiguration applicationConfiguration;

    private ConnectionSource connectionSource;

    // Second generic parameter appears to be wrong, should match the type of ID field
    private Dao<GmailMessage, String> messageDao;
    private Dao<GmailLabelUpdate, String> labelDao;
    private Dao<Schema, String> schemaDao;
    private Dao<HistoryEvent, String> historyDao;
    private Dao<User, String> userDao;

    @PostConstruct
    public void postConstruct() throws SQLException { // Throwing on @PostConstruct method will cause application to exit
        log.info("DaoOwner postconstruct");

        connectionSource = new JdbcConnectionSource(applicationConfiguration.databaseUrl);
        schemaDao = DaoManager.createDao(connectionSource, Schema.class);
        labelDao = DaoManager.createDao(connectionSource, GmailLabelUpdate.class);
        messageDao = DaoManager.createDao(connectionSource, GmailMessage.class);
        historyDao = DaoManager.createDao(connectionSource, HistoryEvent.class);
        userDao = DaoManager.createDao(connectionSource, User.class);
    }

    public ConnectionSource getConnectionSource() {
        return connectionSource;
    }

    public Dao<GmailMessage, String> getMessageDao() {
        return messageDao;
    }

    public Dao<GmailLabelUpdate, String> getLabelDao() {
        return labelDao;
    }

    public Dao<Schema, String> getSchemaDao() {
        return schemaDao;
    }

    public Dao<HistoryEvent, String> getHistoryDao() {
        return historyDao;
    }

    public Dao<User, String> getUserDao() {
        return userDao;
    }
}
