package pack.service.google.gmail;

import com.google.api.services.gmail.model.*;
import com.j256.ormlite.dao.Dao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pack.persist.DaoOwner;
import pack.persist.data.HistoryEvent;
import pack.persist.data.User;
import pack.service.UserService;

import javax.annotation.PostConstruct;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Component
public class DataPreparationService {

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());

    // Return a string with a human-readable date followed by the toString() form of the array
    public static String getHumanReadableDateAndArray(int elementContainingDateTime, String[] stringArray) {

        List<String> resultRowForOutput = new ArrayList<>(Arrays.asList(stringArray));
        String elementContainingDateAsMillis = resultRowForOutput.get(elementContainingDateTime);
        long dateAsMillis = Long.parseLong(elementContainingDateAsMillis);
        return new Date(dateAsMillis).toString() + " " + Arrays.toString(stringArray);
    }

    public void populateEntityObjectFromApi(HistoryEvent historyEventToPersist, History historyEventFromApi) throws SQLException {
        boolean verboseInfo = false;
        int countMessageAdded = 0;
        int countUnreadAdded = 0;
        int countMessageRemoved = 0;
        int countUnreadRemoved = 0;

        List<HistoryLabelAdded> labelsAdded = historyEventFromApi.getLabelsAdded();
        if (labelsAdded != null) {
            for (HistoryLabelAdded labelAddedHistory : labelsAdded) {
                String messageId = labelAddedHistory.getMessage().getId();
                List<String> labelsPreviouslyOnMessage = labelAddedHistory.getMessage().getLabelIds();
                List<String> labelIdsAdded = labelAddedHistory.getLabelIds();
                if (labelIdsAdded != null) {
                    if (labelIdsAdded.contains(GmailDataService.LABEL_INBOX)) {
                        log.info(" -- LABEL ADDED -- INBOX label was added to message: " + messageId);
                        verboseInfo = true;
                        countMessageAdded++;

                        if (labelsPreviouslyOnMessage.contains(GmailDataService.LABEL_UNREAD)) {
                            // An unread message had label inbox removed.  Count this as one fewer unread messages in the inbox
                            log.info(" -- UNREAD ADDED TO INBOX -- An UNREAD message had its INBOX label added: " + messageId);
                            verboseInfo = true;
                            countUnreadAdded++;
                        }

                    }
                    if (labelIdsAdded.contains(GmailDataService.LABEL_UNREAD)) {
                        log.info(" -- LABEL ADDED -- UNREAD label was added to message: " + messageId);
                        verboseInfo = true;
                        countUnreadAdded++;
                    }
                }
            }
        }

        List<HistoryLabelRemoved> labelsRemoved = historyEventFromApi.getLabelsRemoved();
        if (labelsRemoved != null) {
            for (HistoryLabelRemoved labelRemovedHistory : labelsRemoved) {
                Message messageOfRemovedLabel = labelRemovedHistory.getMessage();
                String messageId = messageOfRemovedLabel.getId();
                List<String> labelsRemainingOnMessage = messageOfRemovedLabel.getLabelIds();
                if (labelsRemainingOnMessage == null) {
                    labelsRemainingOnMessage = new ArrayList<>();
                }

                List<String> labelIdsRemoved = labelRemovedHistory.getLabelIds();
                if (labelIdsRemoved != null) {
                    if (labelIdsRemoved.contains(GmailDataService.LABEL_INBOX)) {
                        log.info(" -- LABEL REMOVED -- INBOX label was removed from a message: " + messageId);
                        verboseInfo = true;
                        countMessageRemoved++;

                        if (labelsRemainingOnMessage.contains(GmailDataService.LABEL_UNREAD)) {
                            // An unread message had label inbox removed.  Count this as one fewer unread messages in the inbox
                            log.info(" -- UNREAD REMOVED FROM INBOX -- An UNREAD message had its INBOX label removed: " + messageId);
                            verboseInfo = true;
                            countUnreadRemoved++;
                        }
                    }

                    if (labelIdsRemoved.contains(GmailDataService.LABEL_UNREAD)) {
                        log.info(" -- LABEL REMOVED -- UNREAD label was removed from a message: " + messageId);
                        verboseInfo = true;
                        countUnreadRemoved++;
                    }
                }
            }
        }

        List<HistoryMessageAdded> messagesAdded = historyEventFromApi.getMessagesAdded();
        if (messagesAdded != null) {
            for (HistoryMessageAdded messageAddedHistory : messagesAdded) {
                String messageId = messageAddedHistory.getMessage().getId();
                List<String> labelsOnMessageAdded = messageAddedHistory.getMessage().getLabelIds();
                if (labelsOnMessageAdded != null) {
                    if (labelsOnMessageAdded.contains(GmailDataService.LABEL_INBOX)) {
                        log.info(" -- MESSAGE ADDED -- A new message was added to the mailbox, with label INBOX: " + messageId);
                        verboseInfo = true;
                        countMessageAdded++;
                    }
                    if (labelsOnMessageAdded.contains(GmailDataService.LABEL_UNREAD)) {
                        log.info(" -- MESSAGE ADDED -- A new message was added to the mailbox, with label UNREAD: " + messageId);
                        verboseInfo = true;
                        countUnreadAdded++;
                    }
                    if (labelsOnMessageAdded.contains(GmailDataService.LABEL_SPAM)) {
                        // TODO consider tracking this at some point
                    }
                }
            }
        }

        List<HistoryMessageDeleted> messagesDeleted = historyEventFromApi.getMessagesDeleted();
        if (messagesDeleted != null) {
            for (HistoryMessageDeleted messageDeletedHistory : messagesDeleted) {
                String messageId = messageDeletedHistory.getMessage().getId();
                List<String> labelsOnMessageDeleted = messageDeletedHistory.getMessage().getLabelIds();
                if (labelsOnMessageDeleted != null) {
                    if (labelsOnMessageDeleted.contains(GmailDataService.LABEL_INBOX)) {
                        log.info(" -- MESSAGE DELETED -- A new message was deleted from the mailbox, with label INBOX: " + messageId);
                        verboseInfo = true;
                        countMessageRemoved++;
                    }
                    if (labelsOnMessageDeleted.contains(GmailDataService.LABEL_UNREAD)) {
                        log.info(" -- MESSAGE DELETED -- A new message was deleted from the mailbox, with label INBOX: " + messageId);
                        verboseInfo = true;
                        countUnreadRemoved++;
                    }
                }
            }
        }

        // API reference actually recommends ignoring this section
        // List<Message> messages = historyEventFromApi.getMessages();
        // if (messages != null) {
        //     for (Message message : messages) {
        //         // log.info("Processed history event for message: " + message);
        //     }
        // }

        historyEventToPersist.setMessagesAdded(countMessageAdded);
        historyEventToPersist.setMessagesRemoved(countMessageRemoved);
        historyEventToPersist.setUnreadAdded(countUnreadAdded);
        historyEventToPersist.setUnreadRemoved(countUnreadRemoved);


        if (verboseInfo) {
            log.info("Processed history event from api: " + historyEventFromApi);
        }
    }
}
