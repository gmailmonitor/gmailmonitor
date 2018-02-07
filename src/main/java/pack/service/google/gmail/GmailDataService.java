package pack.service.google.gmail;

import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.Message;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.dao.RawRowMapper;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.stmt.Where;
import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pack.persist.DaoOwner;
import pack.persist.data.GmailLabelUpdate;
import pack.persist.data.GmailMessage;
import pack.persist.data.HistoryEvent;
import pack.persist.data.User;
import pack.persist.data.dummy.GmailLabelUpdateDummy;
import pack.service.UserService;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Component
public class GmailDataService {

    private static final Logger log = LoggerFactory.getLogger((new Object() {}).getClass().getEnclosingClass());

    public static final String LABEL_UNREAD = "UNREAD";
    public static final String LABEL_SPAM = "SPAM";
    public static final String LABEL_INBOX = "INBOX";


    @Autowired private UserService userService;
    @Autowired private DaoOwner daoOwner;
    @Autowired private DataPreparationService dataPreparationService;

    private Dao<GmailMessage, String> messageDao;
    private Dao<GmailLabelUpdate, String> labelDao;
    private Dao<HistoryEvent, String> historyDao;

    @PostConstruct
    private void postConstruct() {
        messageDao = daoOwner.getMessageDao();
        labelDao = daoOwner.getLabelDao();
        historyDao = daoOwner.getHistoryDao();
    }







    public void updateGmailMessage(GmailMessage messageToUpdate) throws SQLException {
        messageDao.update(messageToUpdate);
    }

    public void updateGmailLabel(GmailLabelUpdate labelToUpdate) throws SQLException {
        labelDao.update(labelToUpdate);
    }

    public MessagesMergeResults mergeNewOrUpdatedMessages(User user, List<Message> inboxMessages) throws SQLException {
        // a SelectArg here to generate a ? in statement string below, makes queryBuilder re-usable for each message
        QueryBuilder<GmailMessage, String> qb = messageDao.queryBuilder();
        qb.where()
                .eq(GmailMessage.FIELD_MESSAGE_ID, new SelectArg())
                .and()
                .eq(GmailMessage.FIELD_USER_ID, user.getId());

        int newMessagesAdded = 0;
        int existingMessagesModified = 0;
        int existingMessagesUnchanged = 0;

        // for each message
        for (Message nextMessage : inboxMessages) {
            final GenericRawResults<GmailMessage> persistedMessagesResults = messageDao.queryRaw(qb.prepareStatementString(), messageDao.getRawRowMapper(), nextMessage.getId());
            final List<GmailMessage> persistedMessages = persistedMessagesResults.getResults();
            final int numberOfMatches = persistedMessages.size();
            final String newThreadId = nextMessage.getThreadId();
            final Long newInternalDate = nextMessage.getInternalDate();

            if (numberOfMatches == 1) {
                // Found matching message, so merge/update data
                boolean updated = false;
                final GmailMessage firstResult = persistedMessages.get(0);
                final String oldThreadId = firstResult.getThreadId();

                if (hasThreadIdChanged(newThreadId, oldThreadId)) {
                    log.info("ThreadId has changed for message: " + firstResult.getId() + " old value: ");
                    firstResult.setThreadId(newThreadId);
                    updated = true;
                }

                if (updated) {
                    messageDao.update(firstResult);
                    existingMessagesModified++;
                } else {
                    existingMessagesUnchanged++;
                }

            } else if (numberOfMatches == 0) {
                // No matching message, create a new one
                GmailMessage gmailMessageToPersist = new GmailMessage(nextMessage.getId(), newThreadId);

                gmailMessageToPersist.setUserId(user.getId());
                gmailMessageToPersist.setInternalDate(newInternalDate);
                messageDao.create(gmailMessageToPersist);
                newMessagesAdded++;

            } else {
                log.warn("Unexpected number of matches on existing message ID " + nextMessage.getId() + " for user " + user.getGoogleUserId() + ", " + numberOfMatches + " matches were found");
                persistedMessages.forEach(gmailMessage -> log.warn(gmailMessage.toString()));
            }
        }

        log.info("Messages total: " + messageDao.countOf() + "  Added " + newMessagesAdded + " messages, updated " + existingMessagesModified + " messages, " + existingMessagesUnchanged + " messages unchanged.");
        MessagesMergeResults mergeResults = new MessagesMergeResults();
        mergeResults.setNewAdded(newMessagesAdded);
        mergeResults.setExistingUnchanged(existingMessagesUnchanged);
        mergeResults.setExistingModified(existingMessagesModified);

        return mergeResults;
    }

    private boolean hasThreadIdChanged(String newThreadId, String oldThreadId) {
        return (oldThreadId != null || newThreadId != null)
                && !oldThreadId.equals(newThreadId);
    }


    // Update the database using the collection of History events.  Will create and/or update history event records
    public void persistAndUpdateHistoryData(int userId, List<History> historyEventsFromApi) throws SQLException {
        int historyEventsNew = 0;
        int historyEventsAlreadyPersisted = 0;
        int historyEventErrors = 0;


        // Determine date to assign to new/updated history event objects
        BigInteger smallestHistoryId = findSmallestHistoryId(historyEventsFromApi);
        List<HistoryEvent> historyEventsWithDate = getHistoryEventsWithDateStartingAt(userId, smallestHistoryId);

        for (History nextHistoryEventFromApi : historyEventsFromApi) {
            // See if a record for this history ID already exists in the DB
            final List<HistoryEvent> alreadyPersistedHistoryEvents = getHistoryEvent(userId, nextHistoryEventFromApi.getId());

            if (alreadyPersistedHistoryEvents.isEmpty()) {
                //Create new event
                HistoryEvent historyEventToPersist = new HistoryEvent();
                historyEventToPersist.setUserId(userId);
                historyEventToPersist.setHistoryId(nextHistoryEventFromApi.getId().longValue());
                historyEventToPersist.setJson(nextHistoryEventFromApi.toString());
                dataPreparationService.populateEntityObjectFromApi(historyEventToPersist, nextHistoryEventFromApi);

                // Set estimated date of this event, plus realtime observation status
                HistoryEvent firstEventPreceding = getFirstHistoryPreceding(historyEventsWithDate, nextHistoryEventFromApi.getId());
                HistoryEvent firstEventFollowing = getFirstHistoryFollowing(historyEventsWithDate, nextHistoryEventFromApi.getId());

                // The Pub/Sub event is added to the history AFTER the events that triggered it and therefore has a greater HistoryId
                // Therefore, when ordered by HistoryId the Pub/Sub event (push notification) will appear to FOLLOW (not Precede) the history events that it should be grouped with
                if (firstEventFollowing == null) {
                    log.warn("Unexpected: No history events in database yet for user " + userId); // This code is usually called after a push notification
                    if (firstEventPreceding == null) {
                        historyEventToPersist.setStatusObserved(HistoryEvent.FIELD_STATUS_OBSERVED__STATUS_OBSERVED_FIRSTUPDATE); // No dated events before or after
                    } else {
                        historyEventToPersist.setStatusObserved(HistoryEvent.FIELD_STATUS_OBSERVED__STATUS_OBSERVED_DELAY); // No dated events following, but not the first event.  Maybe events were missed?
                    }
                    historyEventToPersist.setDateOccurred(System.currentTimeMillis());

                } else {
                    long dateOccurred = firstEventFollowing.getDateOccurred();
                    int statusObserved = firstEventFollowing.getStatusObserved();
                    historyEventToPersist.setDateOccurred(dateOccurred);
                    historyEventToPersist.setStatusObserved(statusObserved);
                }

                log.debug("Persisting new history event, id: " + historyEventToPersist.getHistoryId() + " est. date: " + historyEventToPersist.getDateOccurred() + " realtime observed status: " + historyEventToPersist.getStatusObserved());
                historyDao.create(historyEventToPersist);
                historyEventsNew++;


            } else if (alreadyPersistedHistoryEvents.size() == 1) {

                // Update existing event
                HistoryEvent historyEventToUpdate  = alreadyPersistedHistoryEvents.get(0);
                log.warn("Updating existing history event, id: " + historyEventToUpdate.getHistoryId() + " est. date: " + historyEventToUpdate.getDateOccurred() + " realtime observed status: " + historyEventToUpdate.getStatusObserved());

                int hashBefore = historyEventToUpdate.hashCode();
                dataPreparationService.populateEntityObjectFromApi(historyEventToUpdate, nextHistoryEventFromApi); // Repopulate field data

                historyDao.update(historyEventToUpdate);
                historyEventsAlreadyPersisted++;

                int hashAfter = historyEventToUpdate.hashCode();
                if (hashBefore != hashAfter) {
                    log.warn("Unexpected:  Something changed for history event id: " + historyEventToUpdate.getHistoryId());
                }

            } else if (alreadyPersistedHistoryEvents.size() > 1) {
                // Primary key duplication?  Should absolutely never happen
                log.info("Unexpected:  More than one history event persisted!  historyId: " + nextHistoryEventFromApi.getId());
                historyEventErrors++;
                throw new RuntimeException();
            }
        }

        log.info("Processed " + historyEventsFromApi.size() + " history events from API, new added: " + historyEventsNew + ", already persisted: " + historyEventsAlreadyPersisted + ", errors: " + historyEventErrors);
    }

    private List<HistoryEvent> getHistoryEvent(int userId, BigInteger historyId) throws SQLException {
        HashMap<String, Object> queryMap = new HashMap<>();
        queryMap.put(HistoryEvent.FIELD_HISTORY_ID, historyId);
        queryMap.put(HistoryEvent.FIELD_USER_ID, userId);
        List<HistoryEvent> queryResults = historyDao.queryForFieldValues(queryMap);
        return queryResults;
    }

    public HistoryEvent getFirstHistoryPreceding(List<HistoryEvent> historyEventsWithDates, BigInteger historyIdToSearch) {
        HistoryEvent previousEvent = null;
        for (HistoryEvent nextHistoryEvent : historyEventsWithDates) {
            if (nextHistoryEvent.getHistoryId() > historyIdToSearch.longValue()) {
                return previousEvent;
            }
            previousEvent = nextHistoryEvent;
        }
        return null;
    }

    public HistoryEvent getFirstHistoryFollowing(List<HistoryEvent> historyHavingDate, BigInteger historyIdToSearch) {
        for (HistoryEvent nextHistoryEvent : historyHavingDate) {
            if (nextHistoryEvent.getHistoryId() > historyIdToSearch.longValue()) {
                return nextHistoryEvent;
            }
        }
        return null;
    }

    private static Comparator<History> HistoryComparator = new Comparator<History>() {
        @Override
        public int compare(History o1, History o2) {
            return o1.getId().compareTo(o2.getId());
        }
    };

    public static BigInteger findSmallestHistoryId(List<History> historyEventsFromApi) {
        ArrayList<History> clonedList = new ArrayList<>(historyEventsFromApi);
        clonedList.sort(HistoryComparator);

        BigInteger firstId = clonedList.get(0).getId();
        // BigInteger lastId = clonedList.get(clonedList.size()-1).getId();
        // log.info("First HistoryId: " + firstId + " last HistoryId: " + lastId); // Made sure the sort order is what was expected
        return firstId;
    }

    public GmailLabelUpdate getLastLabelUpdate(int userId) throws SQLException {
        PreparedQuery<GmailLabelUpdate> preparedQuery = labelDao.queryBuilder().limit(1L)
                .orderBy(GmailLabelUpdate.FIELD_UPDATE_TIME_MILLIS, false)
                .where()
                .eq(GmailLabelUpdate.FIELD_USER_ID, userId)
                .prepare();
        List<GmailLabelUpdate> results = labelDao.query(preparedQuery);
        if (results.size() == 0) {
            return null;
        }
        return results.get(0);
    }

    public HistoryEvent getLastHistoryUpdate(int userId) throws SQLException {
        PreparedQuery<HistoryEvent> preparedQuery = historyDao.queryBuilder().limit(1L)
                .orderBy(HistoryEvent.FIELD_DATE_OCCURRED, false)
                .where()
                .eq(HistoryEvent.FIELD_USER_ID, userId)
                .prepare();
        List<HistoryEvent> results = historyDao.query(preparedQuery);
        if (results.size() == 0) {
            return null;
        }
        return results.get(0);
    }

    // Get history records that we know the date/time for
    public List<HistoryEvent> getHistoryEventsWithDateStartingAt(int userId, BigInteger startingHistoryId) throws SQLException {
        PreparedQuery<HistoryEvent> preparedQuery = historyDao.queryBuilder()
                .orderBy(HistoryEvent.FIELD_HISTORY_ID, true)
                .where()
                .eq(HistoryEvent.FIELD_USER_ID, userId)
                .and()
                .ge(HistoryEvent.FIELD_HISTORY_ID, startingHistoryId)
                .and()
                .ge(HistoryEvent.FIELD_DATE_OCCURRED, 0)
                .prepare();
        List<HistoryEvent> results = historyDao.query(preparedQuery);
        return results;
    }

    // Get history records that we don't know the date/time for
    public List<HistoryEvent> getHistoryEventsWithoutDateStartingAt(int userId, BigInteger startingHistoryId) throws SQLException {
        PreparedQuery<HistoryEvent> preparedQuery = historyDao.queryBuilder()
                .orderBy(HistoryEvent.FIELD_HISTORY_ID, true)
                .where()
                .eq(HistoryEvent.FIELD_USER_ID, userId)
                .and()
                .ge(HistoryEvent.FIELD_HISTORY_ID, startingHistoryId)
                .and()
                .eq(HistoryEvent.FIELD_DATE_OCCURRED, 0)
                .prepare();
        List<HistoryEvent> results = historyDao.query(preparedQuery);
        return results;
    }

    // Get status for all labels
    public List<GmailLabelUpdate> getLabelUpdates() throws SQLException {
        List<GmailLabelUpdate> gmailLabelUpdates = labelDao.queryForAll();
        return gmailLabelUpdates;
    }




    public List<HistoryEvent> getMailboxMessageChanges(int userIdLoggedIn, long dataStartTime) throws SQLException {
        boolean userExists = userService.userExistsWithId(userIdLoggedIn);
        if (userExists == false) {
            log.info("Warning, logged-in user was not found, userId: " + userIdLoggedIn);
            return new ArrayList<>();
        }

        QueryBuilder<HistoryEvent, String> HistoryEventStringQueryBuilder = historyDao.queryBuilder();
        // Where
        HistoryEventStringQueryBuilder.where()
                .eq(HistoryEvent.FIELD_USER_ID, userIdLoggedIn)
                .and()
                .ge(HistoryEvent.FIELD_DATE_OCCURRED, dataStartTime) // Only records having a date estimate;
                .and() // Only history events believed to occur after the user first authenticated
                .not().eq(HistoryEvent.FIELD_STATUS_OBSERVED, HistoryEvent.FIELD_STATUS_OBSERVED__STATUS_OBSERVED_FIRSTUPDATE);

        List<HistoryEvent> results = HistoryEventStringQueryBuilder.query();
        return results;
    }

    // Select label updates (total/unread message counts) starting a certain number of days ago
    // Since observations may not be at regular intervals, group by 'time buckets' and present the average from each bucket
    public List<GmailLabelUpdate> getMessageAndUnreadCountClose(int userIdLoggedIn, long chartTimeStart) throws SQLException {
        boolean userExists = userService.userExistsWithId(userIdLoggedIn);
        if (userExists == false) {
            log.info("Warning, logged-in user was not found, userId: " + userIdLoggedIn);
            return new ArrayList<>();
        }

        long bucketSizeMillis = calculateBucketSizeFromStartTime(chartTimeStart);
        List<GmailLabelUpdate> gmailLabelUpdates = selectLabelUpdateInterval(userIdLoggedIn, chartTimeStart);


        // Prepare list to be sorted by time index
        List<Object> combinedListForSorting = prepareObjectListForSorting(chartTimeStart, bucketSizeMillis, gmailLabelUpdates);
        sortCombinedList(combinedListForSorting);

        List<GmailLabelUpdate> lastGmailLabelUpdatePerBucket = filterLastLabelUpdatesFromSortedListBuckets(combinedListForSorting);

        return lastGmailLabelUpdatePerBucket;
    }

    private List<GmailLabelUpdate> filterLastLabelUpdatesFromSortedListBuckets(List<Object> combinedListForSorting) {
        GmailLabelUpdate lastUpdate = null;
        List<GmailLabelUpdate> returnList = new ArrayList<>();
        for (Object nextObject : combinedListForSorting) {
            if (nextObject instanceof GmailLabelUpdate) {
                GmailLabelUpdate labelUpdate = (GmailLabelUpdate)nextObject;
                lastUpdate = labelUpdate;
            } else {
                if (lastUpdate != null) {
                    returnList.add(lastUpdate);
                    lastUpdate = null;
                }
            }
        }

        if (lastUpdate != null) {
            returnList.add(lastUpdate);
        }
        return returnList;
    }

    private List<Object> prepareObjectListForSorting(long chartTimeStart, long bucketSizeMillis, List<GmailLabelUpdate> gmailLabelUpdates) {
        List<Object> combinedListForSorting = new ArrayList<>();
        combinedListForSorting.addAll(gmailLabelUpdates);
        for (long bucketStart = chartTimeStart; bucketStart <= System.currentTimeMillis(); bucketStart += bucketSizeMillis) {
            combinedListForSorting.add(bucketStart); // Add this time index as Long
        }
        return combinedListForSorting;
    }

    private List<Object> sortCombinedList(List<Object> combinedListForSorting) {

        Comparator<Object> complexComparator = new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                if (o1 == null || o2 == null) {
                    throw new RuntimeException("Unexpected:  One or more sort objects was null");
                }

                long timeIndex1 = getTimeIndex(o1);
                long timeIndex2 = getTimeIndex(o2);

                if (o1.getClass().equals(o2.getClass())) {
                    if (timeIndex1 == timeIndex2) {

                        // Unclear if this should ever happen in the case of GmailLabelUpdate
                        log.warn("Two data items of type: " + o1.getClass() + " have the same time index: " + timeIndex1);
                    }

                    int compareResult = Long.compare(timeIndex1, timeIndex2);
                    return compareResult;
                }

                // Classes not equal
                if (timeIndex1 == timeIndex2) {
                    // Do this trick where Long's (Closing the old bucket) always get sorted before label updates
                    if (o1 instanceof Long) {
                        // Assumes (Long)o1 (GmailLabelUpdate)o2
                        return -1; // o1 comes first
                    } else {
                        // Assumes (GmailLabelUpdate)o1 (Long)o2
                        return 1;
                    }
                }

                // Classes and time-stamps not equal
                int compareResult = Long.compare(timeIndex1, timeIndex2);
                return compareResult;
            }

            private long getTimeIndex(Object input) {
                if (input instanceof Long) {
                    Long longAsBoxedObject = (Long) input;
                    return longAsBoxedObject.longValue();
                } else if (input instanceof GmailLabelUpdate) {
                    GmailLabelUpdate gmailLabelUpdate = (GmailLabelUpdate) input;
                    return gmailLabelUpdate.getUpdateTimeMillis();
                } else {
                    throw new RuntimeException("Object of unexpected type: " + input.getClass().getName());
                }
            }
        };

        Collections.sort(combinedListForSorting, complexComparator);
        return combinedListForSorting;
    }

    private List<GmailLabelUpdate> selectLabelUpdateInterval(int userIdLoggedIn, long chartTimeStart) throws SQLException {
        QueryBuilder<GmailLabelUpdate, String> gmailLabelUpdateStringQueryBuilder = labelDao.queryBuilder();
        // Select *
        // Where
        gmailLabelUpdateStringQueryBuilder.where()
                .eq(GmailLabelUpdate.FIELD_USER_ID, userIdLoggedIn)
                .and()
                .ge(GmailLabelUpdate.FIELD_UPDATE_TIME_MILLIS, chartTimeStart);
        // Order by
        gmailLabelUpdateStringQueryBuilder.orderBy(GmailLabelUpdate.FIELD_UPDATE_TIME_MILLIS, true);

        List<GmailLabelUpdate> results = gmailLabelUpdateStringQueryBuilder.query();
        return results;
    }


    // Select label updates (total/unread message counts) starting a certain number of days ago
    // Since observations may not be at regular intervals, group by 'time buckets' and present the average from each bucket
    public List<GmailLabelUpdateDummy> getMessageAndUnreadCountAggregated(int userIdLoggedIn, long chartTimeStart) throws SQLException {
        boolean userExists = userService.userExistsWithId(userIdLoggedIn);
        if (userExists == false) {
            log.info("Warning, logged-in user was not found, userId: " + userIdLoggedIn);
            return new ArrayList<>();
        }

        long bucketSizeInMillis = calculateBucketSizeFromStartTime(chartTimeStart);
        QueryBuilder<GmailLabelUpdate, String> gmailLabelUpdateStringQueryBuilder = labelDao.queryBuilder();
        // Select
        gmailLabelUpdateStringQueryBuilder.selectRaw("FLOOR(" +
                "(" + GmailLabelUpdate.FIELD_UPDATE_TIME_MILLIS + " - " + chartTimeStart + ") / " + bucketSizeInMillis + ")" +
                " * " + bucketSizeInMillis + " + " + chartTimeStart +
                " AS bucket");
        gmailLabelUpdateStringQueryBuilder.selectRaw("AVG(messagesTotal) AS avgTotal");
        gmailLabelUpdateStringQueryBuilder.selectRaw("AVG(messagesUnread) AS avgUnread");

        // Where
        gmailLabelUpdateStringQueryBuilder.where()
                .eq(GmailLabelUpdate.FIELD_USER_ID, userIdLoggedIn)
                .and()
                .ge(GmailLabelUpdate.FIELD_UPDATE_TIME_MILLIS, chartTimeStart);

        // Group/Order By
        gmailLabelUpdateStringQueryBuilder.groupByRaw("bucket");
        gmailLabelUpdateStringQueryBuilder.orderByRaw("bucket");
        GenericRawResults<String[]> rawResults = gmailLabelUpdateStringQueryBuilder.queryRaw();
        List<String[]> sqlQueryResults = rawResults.getResults();

        List<GmailLabelUpdateDummy> labelUpdateDummyResults = new ArrayList<>();
        int rowNum = 1;
        for (String[] sqlQueryResult : sqlQueryResults) {
            String messageReadable = DataPreparationService.getHumanReadableDateAndArray(0, sqlQueryResult);
            log.info("getMessageAndUnreadCountAggregated Result " + rowNum + ": " + messageReadable);

            GmailLabelUpdateDummy labelUpdateDummy = new GmailLabelUpdateDummy();
            labelUpdateDummy.setUpdateTimeMillis(Long.parseLong(sqlQueryResult[0]));
            labelUpdateDummy.setMessagesTotal(Integer.parseInt(sqlQueryResult[1]));
            labelUpdateDummy.setMessagesUnread(Integer.parseInt(sqlQueryResult[2]));
            labelUpdateDummyResults.add(labelUpdateDummy);
            rowNum++;
        }

        return labelUpdateDummyResults;
    }


    private long calculateBucketSizeFromStartTime(long chartTimeStart) {
        long bucketSizeUsingChartTime = (System.currentTimeMillis() - chartTimeStart) / 60;

        // If chartTimeStart is very close to current time, this can lead to suggesting a ridiculous number of buckets
        long minimumBucketSize = TimeUnit.SECONDS.toMillis(10);
        long bucketSizeToReturn = Math.max(bucketSizeUsingChartTime, minimumBucketSize);
        return bucketSizeToReturn;
    }


    public GmailLabelUpdate addGmailLabelUpdate(User user, long updateTime, Label labelFromApi) throws SQLException {
        GmailLabelUpdate gmailLabelUpdate = new GmailLabelUpdate();
        gmailLabelUpdate.setUserId(user.getId());
        gmailLabelUpdate.setLabelName(labelFromApi.getName());
        gmailLabelUpdate.setMessagesTotal(labelFromApi.getMessagesTotal());
        gmailLabelUpdate.setMessagesUnread(labelFromApi.getMessagesUnread());
        gmailLabelUpdate.setThreadsTotal(labelFromApi.getThreadsTotal());
        gmailLabelUpdate.setThreadsUnread(labelFromApi.getThreadsUnread());
        gmailLabelUpdate.setUpdateTimeMillis(updateTime);

        labelDao.create(gmailLabelUpdate);
        return gmailLabelUpdate;
    }

    public List<GmailMessage> getMessagesAfterHistoryId(User user, String hint) throws SQLException {
        QueryBuilder<GmailMessage, String> qb = messageDao.queryBuilder();
        Where<GmailMessage, String> whereInProgress = qb.where().eq(GmailMessage.FIELD_USER_ID, user.getId());
        if (hint != null) {  // This part is added conditionally
            whereInProgress.and().ge(GmailMessage.FIELD_HISTORY_ID, hint);
        }

        // Previously this was sorted by FIELD_INTERNAL_DATE but that is probably not the most correct choice
        qb.orderBy(GmailMessage.FIELD_HISTORY_ID, true); // Field was added later

        final GenericRawResults<GmailMessage> matchingMessagesResultsContainer = messageDao.queryRaw(qb.prepareStatementString(), messageDao.getRawRowMapper());
        return matchingMessagesResultsContainer.getResults();
    }

    // Return the latest history ID we have not filled in the details for,
    // using the label summary info
    public Long getLastExaminedHistoryIdFromLabelInfo(String googleUserId) throws SQLException, IOException {
        User userWithGoogleUserId = userService.getUserWithGoogleUserId(googleUserId);

        QueryBuilder<GmailLabelUpdate, String> qb = labelDao.queryBuilder();

        qb.where().eq(GmailLabelUpdate.FIELD_LABEL_NAME, LABEL_INBOX)
                .and().eq(GmailLabelUpdate.FIELD_USER_ID, userWithGoogleUserId.getId());

        qb.orderBy(GmailLabelUpdate.FIELD_LAST_HISTORY_ID, false);
        qb.limit(1L);

        List<GmailLabelUpdate> results = labelDao.query(qb.prepare());
        if (results.size() == 0) {
            log.info("Unexpected: no label summary records found");
            return 0L;
        }

        GmailLabelUpdate gmailLabelUpdate = results.get(0);


        long lastHistoryId = gmailLabelUpdate.getLastHistoryId();
        log.info("Label summary info indicates last examined history event is: " + lastHistoryId);
        return lastHistoryId;
    }


    // Return the latest history ID we have not filled in the details for
    // By directly examining the history table's contents
    public Long getLastExaminedHistoryIdFromHistory(String googleUserId) throws SQLException, IOException {
        User userWithGoogleUserId = userService.getUserWithGoogleUserId(googleUserId);

        QueryBuilder<HistoryEvent, String> qb = historyDao.queryBuilder();

        qb.where().eq(HistoryEvent.FIELD_USER_ID, userWithGoogleUserId.getId())
                .and().not().eq(HistoryEvent.FIELD_JSON, "");

        qb.orderBy(HistoryEvent.FIELD_HISTORY_ID, false);
        qb.limit(1L);

        List<HistoryEvent> results = historyDao.query(qb.prepare());
        if (results.size() == 0) {
            log.info("Unexpected: no label summary records found");
            return 0L;
        }

        HistoryEvent historyEvent = results.get(0);

        long lastHistoryId = historyEvent.getHistoryId();
        log.info("History info indicates last examined history event is: " + lastHistoryId);
        return lastHistoryId;
    }

    public List<GmailMessage> getMessagesWithoutDetails() throws SQLException {
        QueryBuilder<GmailMessage, String> qb = messageDao.queryBuilder();
        qb.where().isNull(GmailMessage.FIELD_HEADER_FROM)
                .or().isNull(GmailMessage.FIELD_INTERNAL_DATE)
                .or().isNull(GmailMessage.FIELD_HISTORY_ID); // Field was added later

        List<GmailMessage> results = qb.query();
        return results;
    }

    public long getFirstUsableDataForUser(Integer userIdLoggedIn) throws SQLException {
        int expectedResults = 2;

        // check label updates
        QueryBuilder<GmailLabelUpdate, String> gmailLabelUpdateStringQueryBuilder = labelDao.queryBuilder();
        gmailLabelUpdateStringQueryBuilder.selectRaw("min(" + GmailLabelUpdate.FIELD_UPDATE_TIME_MILLIS + ")");
        gmailLabelUpdateStringQueryBuilder.selectRaw("max(" + GmailLabelUpdate.FIELD_UPDATE_TIME_MILLIS + ")");
        gmailLabelUpdateStringQueryBuilder.where().eq(GmailLabelUpdate.FIELD_USER_ID, userIdLoggedIn);
        String[] resultsLabel = gmailLabelUpdateStringQueryBuilder.queryRawFirst();


        if (resultsLabel.length != expectedResults) {
            throw new RuntimeException("Unexpected number of results, expected: " + expectedResults + " actual: " + resultsLabel.length);
        }

        String firstLabelUpdate = resultsLabel[0];
        String lastLabelUpdate = resultsLabel[1];


        QueryBuilder<HistoryEvent, String> gmailHistoryQueryBuilder = historyDao.queryBuilder();
        gmailHistoryQueryBuilder.selectRaw("max(" + HistoryEvent.FIELD_DATE_OCCURRED +")");
        gmailHistoryQueryBuilder.selectRaw("min(" + HistoryEvent.FIELD_DATE_OCCURRED +")");
        gmailHistoryQueryBuilder.where().eq(HistoryEvent.FIELD_USER_ID, userIdLoggedIn)
        .and().not().eq(HistoryEvent.FIELD_STATUS_OBSERVED, HistoryEvent.FIELD_STATUS_OBSERVED__STATUS_OBSERVED_FIRSTUPDATE);
        String[] resultsHistory = gmailHistoryQueryBuilder.queryRawFirst();

        if (resultsHistory.length != expectedResults) {
            throw new RuntimeException("Unexpected number of results, expected: " + expectedResults + " actual: " + resultsHistory.length);
        }

        String firstHistoryUpdate = resultsHistory[0];
        String lastHistoryUpdate = resultsHistory[1];

        if (firstLabelUpdate == null) {
            log.info("User id " + userIdLoggedIn + " has no label updates (new user?)");
            firstLabelUpdate = "" + System.currentTimeMillis(); // Prevents exception when converting to long
        }

        if (firstHistoryUpdate == null) {
            log.info("User id " + userIdLoggedIn + " has no history updates (new user?)");
            firstHistoryUpdate = "" + System.currentTimeMillis(); // Prevents exception when converting to long
        }

        long firstLabelUpdateMillis = Long.parseLong(firstLabelUpdate);
        long firstHistoryUpdateMillis = Long.parseLong(firstHistoryUpdate);

        long updateToUse = Math.min(firstLabelUpdateMillis, firstHistoryUpdateMillis);

        log.info("Requested entire data range for user id " + userIdLoggedIn + ", first Label OR History update was: " + new Date(updateToUse));
        return updateToUse;
    }

    public List<Pair> getSenderStats(User user, Long startTime) throws SQLException {
        String COLUMN__FROM = "COUNT (" + GmailMessage.FIELD_HEADER_FROM + ")";

        // Prepare query
        QueryBuilder<GmailMessage, String> qb = messageDao.queryBuilder();
        qb.selectRaw(GmailMessage.FIELD_HEADER_FROM);
        qb.selectRaw(COLUMN__FROM);

        Where<GmailMessage, String> whereInProgress = qb.where().eq(GmailMessage.FIELD_USER_ID, user.getId());
        if (startTime != null) {
            whereInProgress.and().ge(GmailMessage.FIELD_INTERNAL_DATE, startTime);
        }

        qb.groupBy(GmailMessage.FIELD_HEADER_FROM);
        qb.orderByRaw(COLUMN__FROM + " DESC");

        // Prepare RawRowMapper
        RawRowMapper<Pair> rawRowMapper = (columnNames, resultColumns) -> {
            try {
                Pair returnPair = new Pair(resultColumns[0], Integer.valueOf(resultColumns[1]));
                return returnPair;
            } catch (NumberFormatException e) {
                throw new RuntimeException("Query result was not convertible to a number: " + resultColumns[1], e);
            }
        };

        // Execute query
        GenericRawResults<Pair> results = messageDao.queryRaw(qb.prepareStatementString(), rawRowMapper);
        return results.getResults();
    }

    // Results Object, avoids anti-pattern 'output parameters' of method
    public class MessagesMergeResults {
        private int newAdded;
        private int existingUnchanged;
        private int existingModified;

        public void setNewAdded(int newAdded) {
            this.newAdded = newAdded;
        }

        public int getNewAdded() {
            return newAdded;
        }

        public void setExistingUnchanged(int existingUnchanged) {
            this.existingUnchanged = existingUnchanged;
        }

        public int getExistingUnchanged() {
            return existingUnchanged;
        }

        public void setExistingModified(int existingModified) {
            this.existingModified = existingModified;
        }

        public int getExistingModified() {
            return existingModified;
        }
    }

    // unused code section
    /////////////////////////////////////////////////////////////////

    // public Pair<Long, Long> getTimeRangeMillisFromDuration(long durationMillis) throws SQLException {
    //     long timeRangeEnd = System.currentTimeMillis();
    //     long timeRangeStart = timeRangeEnd - durationMillis;
    //     return new Pair<Long, Long>(timeRangeStart, timeRangeEnd);
    // }
    //
    // private GmailMessage getMessageByIdFromDatabase(String messageId) throws Exception {
    //     GmailMessage gmailMessage = null;
    //
    //     HashMap<String, Object> queryMap = new HashMap<>();
    //     queryMap.put(GmailMessage.FIELD_MESSAGE_ID, messageId);
    //     final List<GmailMessage> messages = messageDao.queryForFieldValues(queryMap);
    //
    //     GmailMessage returnMessage = null;
    //     if (messages.size() > 1) {
    //         throw new Exception("Expected only one message in database with messageId: " + messageId);
    //     } else if (messages.size() == 1) {
    //         returnMessage = messages.get(0);
    //     }
    //
    //     return returnMessage;
    // }
    //
    // public void printLargestSenderInfo(int minimumNumberOfMessages) throws SQLException {
    //     final List<GmailMessage> gmailMessages = messageDao.queryForAll();
    //     HashMap<String, Integer> senderCountMap = new HashMap<>();
    //
    //     for (GmailMessage message : gmailMessages) {
    //         final String key = message.getHeaderFrom();
    //         if (!senderCountMap.containsKey(key)) {
    //             senderCountMap.put(key, 1);
    //         } else {
    //             final Integer oldCount = senderCountMap.get(key);
    //             senderCountMap.put(key, oldCount+1);
    //         }
    //     }
    //
    //     Stream<Map.Entry<String,Integer>> sorted =
    //             senderCountMap.entrySet().stream()
    //                     .filter(p -> p.getValue().compareTo(minimumNumberOfMessages) > 0)
    //                     .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()));
    //     final Object[] objects = sorted.toArray();
    //     for (Object nextObject : objects) {
    //         log.info(nextObject.toString());
    //     }
    // }

    // private BigInteger getLargestHistoryId(List<History> newHistoryEventsFromApi) {
    //     BigInteger largestHistoryId = null;
    //     for (History nextHistory : newHistoryEventsFromApi) {
    //         BigInteger nextHistoryId = nextHistory.getId();
    //
    //         if (nextHistoryId.compareTo(largestHistoryId) > 0) {
    //             largestHistoryId = nextHistoryId;
    //         }
    //     }
    //     return largestHistoryId;
    // }
}
