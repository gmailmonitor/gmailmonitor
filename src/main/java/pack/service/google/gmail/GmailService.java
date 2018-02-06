package pack.service.google.gmail;

import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pack.persist.data.GmailLabelUpdate;
import pack.persist.data.GmailMessage;
import pack.persist.data.User;
import pack.service.UserService;

import java.io.IOException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.*;

// Contains methods performing both Gmail API and database operations
@Component
public class GmailService {

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());



    @Autowired private GmailApiService gmailApiService;
    @Autowired private GmailDataService gmailDataService;
    @Autowired private UserService userService;

    public void resyncInboxAllMessages(User user) throws IOException, SQLException, InterruptedException {

        // ---------- Create a new Label Summary
        GmailLabelUpdate gmailLabelUpdate = retrieveLabelSummaryForUser(user);


        // ---------- Get all messages
        List<com.google.api.services.gmail.model.Message> inboxMessages = gmailApiService.getAllMessagesForLabel(user.getGoogleUserId(), "INBOX");

        // ---------- Record historyId of latest message
        updateGmailLabelUpdateWithLatestHistoryId(user.getGoogleUserId(), gmailLabelUpdate, inboxMessages);

        // ---------- Persist new or update existing messages that were fetched
        GmailDataService.MessagesMergeResults messagesMergeResults = gmailDataService.mergeNewOrUpdatedMessages(user, inboxMessages);

        retrieveMessageDetailsForUser(user.getGoogleUserId()); // "Fill in" missing extra info on messages using api
    }

    public void resyncInboxNewMessagesUpTo(User user, Integer softMaximum) throws IOException, SQLException {
        // ---------- Create a new Label Summary
        GmailLabelUpdate gmailLabelUpdate = retrieveLabelSummaryForUser(user);

        // ---------- Get messages one page at a time until we don't see new messages
        StatefulMessagePageFetcher pageFetcher = gmailApiService.getMessagePageFetcherForLabel(user.getGoogleUserId(), "INBOX", softMaximum);
        int totalMessagesFetched = 0;
        while (true) {
            List<Message> nextPageMessages = pageFetcher.getNextPage();
            if (nextPageMessages == null) {
                break;
            }

            int nextPageSize = nextPageMessages.size();
            totalMessagesFetched += nextPageSize;

            // ---------- Record historyId of latest message
            updateGmailLabelUpdateWithLatestHistoryId(user.getGoogleUserId(), gmailLabelUpdate, nextPageMessages);

            // ---------- Persist new or update existing messages that were fetched
            GmailDataService.MessagesMergeResults mergeResults = gmailDataService.mergeNewOrUpdatedMessages(user, nextPageMessages);

            // ---------- Determine whether to stop fetching new pages
            // Soft maximum now handled by page fetcher, but stop fetching new pages from API if we're past the point of seeing new messages
            // Either catching up on an old user, or something is wrong and we overran when monitoring was started
            boolean tooManyMessagesFetched = totalMessagesFetched > 170;
            // The latest page has many messages we've seen before
            boolean tooManyNotNewMessages = (nextPageSize / 3) <  mergeResults.getExistingModified() + mergeResults.getExistingUnchanged();
            if (tooManyMessagesFetched || tooManyNotNewMessages) {
                log.info("Stopping fetch of new pages, reason - tooManyMessagesFetched " + tooManyMessagesFetched + ", tooManyNotNewMessages:" + tooManyNotNewMessages);
                break;
            }
        }

        System.out.format("Updated label info with last history Id: %s total messages: %s%n", gmailLabelUpdate.getLastHistoryId(), gmailLabelUpdate.getMessagesTotal());
        retrieveMessageDetailsForUser(user.getGoogleUserId()); // "Fill in" missing extra info on messages using api
    }

    public List<Label> getAllLabelsFromGmail(String googleUserId) throws IOException {
        List<Label> labels = gmailApiService.demoGetAllLabels(googleUserId);
        return labels;
    }

    // Set the latest historyId from messages, write it to the provided GmailLabelUpdate
    private void updateGmailLabelUpdateWithLatestHistoryId(String googleUserId, GmailLabelUpdate gmailLabelUpdate, List<Message> messages) throws IOException, SQLException {
        // First message in a page is usually the latest
        final String latestMessageId = messages.get(0).getId();

        // These Message objects from input are not populated with fields including History Id, so details must be fetched
        final Message latestMessage = gmailApiService.getMessageDetailsById(googleUserId, latestMessageId);
        final String historyIdBigIntegerToString = latestMessage.getHistoryId().toString();

        long oldHistoryId = gmailLabelUpdate.getLastHistoryId();
        long nextHistoryId = Long.valueOf(historyIdBigIntegerToString);
        if (oldHistoryId < nextHistoryId) {
            Date labelUpdateTime = new Date(gmailLabelUpdate.getUpdateTimeMillis());

            gmailLabelUpdate.setLastHistoryId(nextHistoryId);
            if (oldHistoryId > 0) {
                log.info("Latest history id of: {} is newer than previous: {}, modifying GmailLabelUpdate object with time {}", nextHistoryId, oldHistoryId, labelUpdateTime);
            } else {
                log.info("Setting new history id of: {} on GmailLabelUpdate object with time {}", nextHistoryId, labelUpdateTime);
            }
        }
        gmailDataService.updateGmailLabel(gmailLabelUpdate);
    }

    private BigInteger getLatestHistoryIdFromMessages(List<Message> messages) {
        BigInteger largestHistoryId = null;
        for (Message nextMessage : messages) {
            BigInteger nextHistoryId = nextMessage.getHistoryId();
            if (nextHistoryId.compareTo(largestHistoryId) == 1) {
                // next is greater than largest
                largestHistoryId = nextHistoryId;
            }
        }
        return largestHistoryId;
    }

    public void updateHistoryStartingWith(int userId, Long historyId) throws IOException, SQLException {
        User user = userService.getUserWithId(userId);
        final List<History> newHistoryEventsFromApi = gmailApiService.getMessageHistoryFrom(user.getGoogleUserId(), historyId);
        if (newHistoryEventsFromApi.isEmpty()) {
            return;
        }
        gmailDataService.persistAndUpdateHistoryData(userId, newHistoryEventsFromApi); // Save raw history events to DB
    }

    // Use the gmail API to populate a new GmailLabelUpdate object with current data, then persist it
    private GmailLabelUpdate retrieveLabelSummaryForUser(User user) throws IOException, SQLException {
        com.google.api.services.gmail.model.Label LabelFromApi = gmailApiService.getLabelInfo(user.getGoogleUserId(), gmailDataService.LABEL_INBOX);

        long updateTime = System.currentTimeMillis();
        GmailLabelUpdate gmailLabelUpdate = gmailDataService.addGmailLabelUpdate(user, updateTime, LabelFromApi);
        return gmailLabelUpdate;
    }



    public Long findNextValidHistoryId(User user, String historyIdHint) throws SQLException, IOException {
        final List<GmailMessage> matchingMessagesResults = gmailDataService.getMessagesAfterHistoryId(user, historyIdHint);

        if (historyIdHint != null && matchingMessagesResults.size() == 0) {
            // We don't have any messages after this history ID that were not already examined
            log.info("No history ID's that haven't been examined after " + historyIdHint);
            return Long.valueOf(historyIdHint);
        }

        log.info("Found " + matchingMessagesResults.size() + " messages with greater history ID");

        int lower = 0;
        int upper = matchingMessagesResults.size()-1;

        while (lower != upper) {
            final Long lowerHistoryId = matchingMessagesResults.get(lower).getHistoryId();
            final Long upperHistoryId = matchingMessagesResults.get(upper).getHistoryId();
            // log.info("Looking for matching history id between " + lower + "(" + lowerHistoryId + ") and " + upper + "(" + upperHistoryId + ")");

            int test = (lower + upper) / 2;

            final Long testHistoryId = matchingMessagesResults.get(test).getHistoryId();

            final boolean isTestHistoryIdValid = gmailApiService.isHistoryIdValid(user.getGoogleUserId(), testHistoryId + "");
            log.debug("History id " + user.getGoogleUserId() + " valid?: " + testHistoryId);


            if (isTestHistoryIdValid) {
                if (upper != test) {
                    upper = test; // reduce upper
                } else {
                    upper--; // upper == test both valid so decrement
                }

            } else {
                if (lower != test) {
                    lower = test; // increase lower
                } else {
                    lower++; // lower == test both invalid so increment
                }
            }
        }

        Long returnHistoryId = matchingMessagesResults.get(lower).getHistoryId();
        String resultMessage = "Found valid history id "
                + "index:" + lower
                + " out of " + matchingMessagesResults.size()
                + " value: " + returnHistoryId + "";
        if (historyIdHint != null) {
            resultMessage += " hint was: " + historyIdHint;
        }
        log.info(resultMessage);

        return returnHistoryId;
    }

    // Fetch and populate message details
    // Gets extra information from message Id (i.e. header information)
    private void retrieveMessageDetailsForUser(String googleUserId) throws SQLException, IOException {
        List<GmailMessage> messagesWithoutDetails = gmailDataService.getMessagesWithoutDetails();

        // As an alternative, this code will update all messages
        // final List<GmailMessage> messagesWithoutDetails = messageDao.queryForAll();

        log.info("Found " + messagesWithoutDetails.size() + " messages without From header data");

        int messagesUpdated = 0;
        for (GmailMessage nextMessageToPopulate : messagesWithoutDetails) {
            final String nextMessageId = nextMessageToPopulate.getMessageId();
            final Message latestPopulatedMessageFromApi = gmailApiService.getMessageDetailsById(googleUserId, nextMessageId);

            final BigInteger historyIdFromApi = latestPopulatedMessageFromApi.getHistoryId();
            if (historyIdFromApi != null) {
                nextMessageToPopulate.setHistoryId(historyIdFromApi.longValue());
            } else {
                log.info("Message from API has no historyId value");
            }

            // Set InternalDate on DB Entity
            final Long internalDateFromApi = latestPopulatedMessageFromApi.getInternalDate();
            if (internalDateFromApi != null) {
                nextMessageToPopulate.setInternalDate(internalDateFromApi);
            } else {
                log.info("Message from API has no InternalDate value");
            }


            // Set "From" header value on DB Entity
            String fromHeaderValue = null;
            final List<MessagePartHeader> messageHeaders = latestPopulatedMessageFromApi.getPayload().getHeaders();
            for (MessagePartHeader header : messageHeaders) {
                if (header.getName().equals("From")) {
                    fromHeaderValue = header.getValue();
                }
            }

            if (fromHeaderValue == null) {
                log.info("Could not find 'From' header for messageId: " + nextMessageId);
            } else {
                nextMessageToPopulate.setHeaderFrom(fromHeaderValue);
            }

            gmailDataService.updateGmailMessage(nextMessageToPopulate); // Assumes there was some change...
            messagesUpdated++;
        }

        log.info("Message Details were updated for " + messagesUpdated + " messages");
        return;
    }
}
