package pack.service.google.gmail;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

// Handles the Gmail API querying for messages and getting the next page of results when requested.
public class StatefulMessagePageFetcher {

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());

    //TODO query is not used for now

    private final String labelId;
    private final Integer maximumMessages;
    private int messagesRetrieved;
    private Gmail gmailService;

    private final String userId = "me";
    private ListMessagesResponse lastResponse;


    public StatefulMessagePageFetcher(Gmail gmailService, String labelId, Integer softMaximum) {
        this.gmailService = gmailService;
        this.labelId = labelId;

        if (softMaximum == null) {
            maximumMessages = Integer.MAX_VALUE;
        } else {
            this.maximumMessages = softMaximum;
        }
    }

    // Returns null if there are no more messages or soft maximum exceeded
    public List<Message> getNextPage() throws IOException {
        if (messagesRetrieved >= maximumMessages) {
            log.info("Number of messages already retrieved: " + messagesRetrieved + " exceeds message limit of :" + maximumMessages + ", not fetching next page");
            return null;
        } else  if (lastResponse == null) {
            // No pages were fetched before, get the first page
             lastResponse = gmailService.users().messages().list(userId).setLabelIds(Arrays.asList(labelId)).execute();

        } else if (lastResponse.getNextPageToken() == null) {
            // There is no next page
            return null;

        } else {
            // There is a "next" page, fetch it
            // TODO what happens if label(e.g. inbox) changes between page fetches?
            // TODO if pg1 messages are removed, would that cause some messages to be skipped when fetching pg2?
            String pageToken = lastResponse.getNextPageToken();
            lastResponse = gmailService.users().messages().list(userId).setLabelIds(Arrays.asList(labelId)).setPageToken(pageToken).execute();
        }

        List<Message> messages = lastResponse.getMessages();
        if (messages == null) {
            // Google API indicated that there *was* a page, upon fetching it we find it to be empty
            // Unclear if this ever happens
            log.info("Note, Google API listed a 'next' page that, when fetched, contained no messages");
            return null;
        }

        log.info("Downloaded  " + messages.size() + " messages,  Est. results size: " + lastResponse.getResultSizeEstimate());
        messagesRetrieved += messages.size();
        return messages;
    }

}
