package pack.persist;

import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.Message;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pack.persist.data.GmailMessage;
import pack.persist.data.HistoryEvent;
import pack.service.google.gmail.DataPreparationService;
import pack.service.google.gmail.GmailDataService;
import pack.service.google.gmail.GmailService;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


// Contains things used in development that are run "manually" by editing code
@Component
public class SpecializedDatabaseTasks {

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());

    @Autowired private DaoOwner daoOwner;
    @Autowired private GmailDataService gmailDataService;
    @Autowired private DataPreparationService dataPreparationService;

    // Uses the JSON already stored to update history event stats such as the added/removed counts, date observed
    public void parseHistoryEventJsonForCountChanges() throws SQLException, IOException {

        Dao<HistoryEvent, String> historyDao = daoOwner.getHistoryDao();
        List<HistoryEvent> historyEvents = historyDao.queryForAll();
        log.info("Got " + historyEvents.size() + " history event records");

        for (HistoryEvent historyEvent : historyEvents) {

            String jsonString = historyEvent.getJson();
            if (jsonString == null || jsonString.isEmpty()) {
                // Nothing to do here
                continue;
            }

            // Used to determine if there were destructive changes
            boolean hasCountChangesBefore = hasCountChanges(historyEvent);
            String countSignatureBefore = getCountSignature(historyEvent);

            History gmailHistory = parseJsonToGoogleApiHistoryEvent(historyEvent);



            if (null == null) {
                historyEvent.setUserId(0);
                throw new RuntimeException("Need to provide user id");
            }

            dataPreparationService.populateEntityObjectFromApi(historyEvent, gmailHistory);
            String countSignatureAfter = getCountSignature(historyEvent);

            boolean countSignatureChanged = !countSignatureBefore.equals(countSignatureAfter);
            if (hasCountChangesBefore & countSignatureChanged) {
                if (countSignatureBefore.equals("0100") & countSignatureAfter.equals("0101")) {
                    // Fixes an existing bug
                } else {
                    log.info("Warning - destructive changes appear to have occurred processing historyId: " + historyEvent.getHistoryId());
                    throw new RuntimeException();
                }
            }

            historyDao.update(historyEvent);
        }
    }

    // A non-automated test to determine if the history event JSON contains any date information.  We expect it (unfortunately) does not.
    public void parseHistoryEventJsonForMissingDateInfo() throws SQLException, IOException {

        Dao<HistoryEvent, String> historyDao = daoOwner.getHistoryDao();
        Dao<GmailMessage, String> messageDao = daoOwner.getMessageDao();

        List<HistoryEvent> historyEvents = historyDao.queryForAll();
        log.info("Got " + historyEvents.size() + " history event records");

        for (HistoryEvent historyEvent : historyEvents) {
            if (historyEvent.getDateOccurred() != 0) {
                // Already has date info
                continue;
            }

            String jsonString = historyEvent.getJson();
            if (jsonString == null || jsonString.isEmpty()) {
                // Nothing to do here
                continue;
            }

            if (! hasCountChanges(historyEvent)) {
                // Mailboxes we care about were not affected
                continue;
            }

            History gmailHistory = parseJsonToGoogleApiHistoryEvent(historyEvent);
            List<Message> messages = gmailHistory.getMessages();

            List<Long> datesFromMessages = new ArrayList<>();
            for (Message nextMessage : messages) {

                Long dateFromHistory = nextMessage.getInternalDate();
                if (dateFromHistory != null) {
                    log.info("Date is surprisingly not null");
                    continue;
                }


                String messageId = nextMessage.getId();
                PreparedQuery<GmailMessage> prepared = messageDao.queryBuilder().where().eq(GmailMessage.FIELD_MESSAGE_ID, messageId).prepare();

                List<GmailMessage> messagesFromQuery = messageDao.query(prepared);
                if (messagesFromQuery.size() > 1) {
                    log.info("Unexpected number of query results");
                    continue;
                }

                if (messagesFromQuery.size() == 0) {
                    log.info("No message was found with the ID from this history event: " + messageId);
                    continue;
                }

                GmailMessage gmailMessage = messagesFromQuery.get(0);

                Long dateFromMessage = gmailMessage.getInternalDate();
                datesFromMessages.add(dateFromMessage);
            }


            if (datesFromMessages.size() >= 2) {
                Collections.sort(datesFromMessages);
                Long first = datesFromMessages.get(0);
                Long last = datesFromMessages.get(datesFromMessages.size() - 1);
                long differenceInMillis = (last - first);
                log.info("Dates in history event differ by : " + differenceInMillis + "ms");
            }


            //
            // // Used to determine if there were destructive changes
            // boolean hasCountChangesBefore = hasCountChanges(historyEvent);
            // String countSignatureBefore = getCountSignature(historyEvent);
            //
            // String countSignatureAfter = getCountSignature(historyEvent);
            //
            // boolean countSignatureChanged = !countSignatureBefore.equals(countSignatureAfter);
            // if (hasCountChangesBefore & countSignatureChanged) {
            //     if (countSignatureBefore.equals("0100") & countSignatureAfter.equals("0101")) {
            //         // Fixes an existing bug
            //     } else {
            //         log.info("Warning - destructive changes appear to have occurred processing historyId: " + historyEvent.getHistoryId());
            //         throw new RuntimeException();
            //     }
            // }
            //
            // historyDao.update(historyEvent);
        }
    }

    private History parseJsonToGoogleApiHistoryEvent(HistoryEvent historyEvent) throws IOException {
        // This also works, but it produces an "arbitrary json" object
        // JsonObject parsed = (JsonObject) parser.parse(json);
        // ((JsonObject) parsed).has("labelsAdded");
        // log.info(parsed);

        String json = historyEvent.getJson();
        JsonObjectParser jsonObjectParser = new JsonObjectParser(JacksonFactory.getDefaultInstance());

        History gmailHistory = jsonObjectParser.parseAndClose(new StringReader(json), History.class);
        return gmailHistory;
    }

    private boolean hasCountChanges(HistoryEvent historyEvent) {
        int messagesAdded = historyEvent.getMessagesAdded();
        int messagesRemoved = historyEvent.getMessagesRemoved();
        int unreadAdded = historyEvent.getUnreadAdded();
        int unreadRemoved = historyEvent.getUnreadRemoved();
        int totalMessages = messagesAdded + messagesRemoved + unreadAdded + unreadRemoved;

        boolean result = totalMessages > 0;
        return result;
    }

    private String getCountSignature(HistoryEvent historyEvent) {
        int messagesAdded = historyEvent.getMessagesAdded();
        int messagesRemoved = historyEvent.getMessagesRemoved();
        int unreadAdded = historyEvent.getUnreadAdded();
        int unreadRemoved = historyEvent.getUnreadRemoved();
        String countSignature = "" + messagesAdded + messagesRemoved + unreadAdded + unreadRemoved;
        return countSignature;
    }

    public void rewriteDatesOnHistoryEventsWithChanges(int userId) throws SQLException {
        List<HistoryEvent> historyEventsWithDate = gmailDataService.getHistoryEventsWithDateStartingAt(userId, BigInteger.ZERO);
        List<HistoryEvent> historyEventsWithoutDateStartingAt = gmailDataService.getHistoryEventsWithoutDateStartingAt(userId, BigInteger.ZERO);

        int hadToLookForwardCount = 0;
        int datesUpdatedCount = 0;

        for (HistoryEvent historyEventWithoutDate : historyEventsWithoutDateStartingAt) {
            if (historyEventWithoutDate.getMessagesAdded() + historyEventWithoutDate.getMessagesRemoved() + historyEventWithoutDate.getUnreadAdded() + historyEventWithoutDate.getUnreadRemoved() == 0) {
                // Has no message changes, not interested in assigning a date to this one
                continue;
            }

            long historyIdToSearchBefore = historyEventWithoutDate.getHistoryId();
            BigInteger historyIdToSearchBeforeBigInteger = BigInteger.valueOf(historyIdToSearchBefore);
            HistoryEvent historyEventToExtractDate = gmailDataService.getFirstHistoryPreceding(historyEventsWithDate, historyIdToSearchBeforeBigInteger);

            if (historyEventToExtractDate == null) {
                System.out.format("Could not find a preceding event for history id %s%n", historyEventWithoutDate.getHistoryId());
                continue;

            }

                // Everything is OK
                historyEventWithoutDate.setDateOccurred(historyEventToExtractDate.getDateOccurred());
                Dao<HistoryEvent, String> historyDao = daoOwner.getHistoryDao();
                historyDao.update(historyEventWithoutDate);
                datesUpdatedCount++;


        }
        System.out.format("Updated %s dates%n", datesUpdatedCount);
    }
}
