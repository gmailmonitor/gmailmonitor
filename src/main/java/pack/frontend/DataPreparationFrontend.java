package pack.frontend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pack.frontend.databind.DataPointMessageCount;
import pack.frontend.databind.DataSeriesLabelChangeSegments;
import pack.frontend.databind.LongPoint;
import pack.persist.data.GmailLabelUpdate;
import pack.persist.data.HistoryEvent;
import pack.persist.data.dummy.GmailLabelUpdateDummy;
import pack.persist.data.dummy.HistoryEventDummy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DataPreparationFrontend {

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());

    public static List<DataPointMessageCount> buildDataSeriesAllMessagesCount(List<GmailLabelUpdate> dataOverTimeMessageAndUnreadCount) {
        List<DataPointMessageCount> dataPointsToReturn = new ArrayList<>();
        for (GmailLabelUpdate nextLabelUpdate : dataOverTimeMessageAndUnreadCount) {
            DataPointMessageCount messageCount = new DataPointMessageCount();
            messageCount.setDateTimeMillis(nextLabelUpdate.getUpdateTimeMillis());
            messageCount.setMessageCount(nextLabelUpdate.getMessagesTotal());
            messageCount.setUnreadCount(nextLabelUpdate.getMessagesUnread());
            dataPointsToReturn.add(messageCount);
        }
        return dataPointsToReturn;
    }

    public static DataSeriesLabelChangeSegments buildDataSeriesForAllCountChanges(List<GmailLabelUpdate> dataOverTimeMessageAndUnreadCount, List<HistoryEvent> dataOverTimeMailboxMessageChanges) {
        GmailLabelUpdate pointToCollectDeltas = null;

        DataSeriesLabelChangeSegments returnDataSeries = new DataSeriesLabelChangeSegments();

        for (GmailLabelUpdate nextPoint : dataOverTimeMessageAndUnreadCount) {
            if (pointToCollectDeltas == null) {
                pointToCollectDeltas = nextPoint; // Nothing to do on first iteration
            }

            HistoryEventDummy aggregatedDeltasForPoint = collectDeltasForPoint(pointToCollectDeltas, nextPoint, dataOverTimeMailboxMessageChanges);// Collects the deltas for the 'previous' point
            if (aggregatedDeltasForPoint.hasChanges()) {
                // Don't collect an object that doesn't describe any mailbox changes
                returnDataSeries.applyDeltas(aggregatedDeltasForPoint, pointToCollectDeltas);
            }

            pointToCollectDeltas = nextPoint; // Deltas are not collected for the last point.
        }

        return returnDataSeries;
    }



    // Assumes HistoryEvent ordered by date ascending
    private static HistoryEventDummy collectDeltasForPoint(GmailLabelUpdate pointToCollectDeltas, GmailLabelUpdate nextPoint, List<HistoryEvent> dataOverTimeMailboxMessageChanges) {
        long timeWindowStart = pointToCollectDeltas.getUpdateTimeMillis();
        long timeWindowEnd = nextPoint.getUpdateTimeMillis();

        HistoryEventDummy eventToReturn = new HistoryEventDummy();

        for (HistoryEvent nextHistoryEvent :  dataOverTimeMailboxMessageChanges) {
            long eventTime = nextHistoryEvent.getDateOccurred();
            if (eventTime < timeWindowStart) {
                continue;
            } else if (timeWindowEnd < eventTime) {
                continue; // assuming data is ordered is outside this concern
            }

            // Accumulate totals onto return event
            eventToReturn.setMessagesAdded(eventToReturn.getMessagesAdded() + nextHistoryEvent.getMessagesAdded());
            eventToReturn.setMessagesRemoved(eventToReturn.getMessagesRemoved() + nextHistoryEvent.getMessagesRemoved());
            eventToReturn.setUnreadAdded(eventToReturn.getUnreadAdded() + nextHistoryEvent.getUnreadAdded());
            eventToReturn.setUnreadRemoved(eventToReturn.getUnreadRemoved() + nextHistoryEvent.getUnreadRemoved());
        }

        return eventToReturn;
    }
}
