package pack.service;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pack.persist.DaoOwner;
import pack.persist.data.HistoryEvent;
import pack.persist.data.User;

import javax.annotation.PostConstruct;
import java.sql.SQLException;
import java.util.List;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger((new Object() {
    }).getClass().getEnclosingClass());

    @Autowired
    private DaoOwner daoOwner;
    private Dao<User, String> userDao;
    private Dao<HistoryEvent, String> historyDao;

    @PostConstruct
    private void postConstruct() {
        userDao = daoOwner.getUserDao();
        historyDao = daoOwner.getHistoryDao();
    }

    // Create a user for this google Id, if one doesn't exist - otherwise do nothing
    public void addUserWithGoogleId(String googleUserId) throws SQLException {
        boolean userExists = userExistsWithGoogleUserId(googleUserId);
        if (userExists == false) {
            // consider recording date created
            log.info("Creating user with google id: " + googleUserId);
            addNewUser(googleUserId);
            logAllUsers();
        }
    }

    private void addNewUser(String googleUserId) throws SQLException {
        User newUser = new User();
        newUser.setGoogleUserId(googleUserId);
        userDao.create(newUser);
    }

    public boolean userExistsWithId(int userId) throws SQLException {
        String userIdToQuery = (new Integer(userId)).toString();
        User user = userDao.queryForId(userIdToQuery);
        if (user != null) {
            return true;
        }
        return false;
    }

    public boolean userExistsWithGoogleUserId(String googleUserId) throws SQLException {
        User user = getUserWithGoogleUserId(googleUserId);
        if (user != null) {
            return true;
        }
        return false;
    }

    // Created for development use
    public User getUserWithId(long userId) throws SQLException {
        QueryBuilder<User, String> qb = userDao.queryBuilder();
        qb.where().eq(User.FIELD_USER_ID, userId);
        List<User> userResults = userDao.queryRaw(qb.prepareStatementString(), userDao.getRawRowMapper()).getResults();

        if (userResults.size() == 0) {
            return null;
        } else if (userResults.size() == 1) {
            return userResults.get(0);
        }

        throw new RuntimeException("Warning - more than one user was found matching userId: " + userId);
    }

    public User getUserWithGoogleUserId(String googleUserId) throws SQLException {
        QueryBuilder<User, String> qb = userDao.queryBuilder();
        qb.where().eq(User.FIELD_GOOGLE_USER_ID, googleUserId);
        List<User> userResults = userDao.queryRaw(qb.prepareStatementString(), userDao.getRawRowMapper()).getResults();

        if (userResults.size() == 0) {
            return null;
        } else if (userResults.size() == 1) {
            return userResults.get(0);
        }

        throw new RuntimeException("Warning - more than one user was found matching googleUserId: " + googleUserId);
    }

    public void logAllUsers() throws SQLException {
        List<User> users = userDao.queryForAll();
        for (User user : users) {
            log.info("User id: " + user.getId() + "  googleId: " + user.getGoogleUserId());
        }
    }

    // Candidate for moving to HistoryService, if one is created
    public Long getFirstHistoryUpdateTimeForUser(long userId) throws SQLException {

        QueryBuilder<HistoryEvent, String> qb = historyDao.queryBuilder();
        qb.where().eq(HistoryEvent.FIELD_USER_ID, userId);
        qb.orderBy(HistoryEvent.FIELD_DATE_OCCURRED, true);
        qb.limit(1L);

        List<HistoryEvent> historyResults = historyDao.queryRaw(qb.prepareStatementString(), historyDao.getRawRowMapper()).getResults();
        if (historyResults.size() == 0) {
            return null;
        }

        long dateOccurred = historyResults.get(0).getDateOccurred();
        return dateOccurred;
    }
}
