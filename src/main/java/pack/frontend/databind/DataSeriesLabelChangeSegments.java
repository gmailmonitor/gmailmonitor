package pack.frontend.databind;

import pack.persist.data.GmailLabelUpdate;
import pack.persist.data.dummy.GmailLabelUpdateDummy;
import pack.persist.data.dummy.HistoryEventDummy;

import java.util.ArrayList;
import java.util.List;

public class DataSeriesLabelChangeSegments {
    private List<LongPoint[]> segmentsMessagesAdded = new ArrayList<>();
    private List<LongPoint[]> segmentsMessagesRemoved = new ArrayList<>();
    private List<LongPoint[]> segmentsUnreadAdded = new ArrayList<>();
    private List<LongPoint[]> segmentsUnreadRemoved = new ArrayList<>();

    public void applyDeltas(HistoryEventDummy aggregatedDeltasForPoint, GmailLabelUpdate pointToCollectDeltas) {
        long baseTime = pointToCollectDeltas.getUpdateTimeMillis();
        int messageCountBase = pointToCollectDeltas.getMessagesTotal();
        int unreadCountBase = pointToCollectDeltas.getMessagesUnread();

        int messageCountAdded = messageCountBase + aggregatedDeltasForPoint.getMessagesAdded();
        int messageCountRemoved = messageCountBase - aggregatedDeltasForPoint.getMessagesRemoved();
        int unreadCountAdded = unreadCountBase + aggregatedDeltasForPoint.getUnreadAdded();
        int unreadCountRemoved = unreadCountBase - aggregatedDeltasForPoint.getUnreadRemoved();

        LongPoint[] messageAddedSegment = {new LongPoint(baseTime, messageCountBase), new LongPoint(baseTime, messageCountAdded)};
        LongPoint[] messageRemovedSegment = {new LongPoint(baseTime, messageCountBase), new LongPoint(baseTime, messageCountRemoved)};
        LongPoint[] unreadAddedSegment = {new LongPoint(baseTime, unreadCountBase), new LongPoint(baseTime, unreadCountAdded)};
        LongPoint[] unreadRemovedSegment = {new LongPoint(baseTime, unreadCountBase), new LongPoint(baseTime, unreadCountRemoved)};

        getSegmentsMessagesAdded().add(messageAddedSegment);
        getSegmentsMessagesRemoved().add(messageRemovedSegment);
        getSegmentsUnreadAdded().add(unreadAddedSegment);
        getSegmentsUnreadRemoved().add(unreadRemovedSegment);
    }

    public List<LongPoint[]> getSegmentsMessagesAdded() {
        return segmentsMessagesAdded;
    }

    public void setSegmentsMessagesAdded(List<LongPoint[]> segmentsMessagesAdded) {
        this.segmentsMessagesAdded = segmentsMessagesAdded;
    }

    public List<LongPoint[]> getSegmentsMessagesRemoved() {
        return segmentsMessagesRemoved;
    }

    public void setSegmentsMessagesRemoved(List<LongPoint[]> segmentsMessagesRemoved) {
        this.segmentsMessagesRemoved = segmentsMessagesRemoved;
    }

    public List<LongPoint[]> getSegmentsUnreadAdded() {
        return segmentsUnreadAdded;
    }

    public void setSegmentsUnreadAdded(List<LongPoint[]> segmentsUnreadAdded) {
        this.segmentsUnreadAdded = segmentsUnreadAdded;
    }

    public List<LongPoint[]> getSegmentsUnreadRemoved() {
        return segmentsUnreadRemoved;
    }

    public void setSegmentsUnreadRemoved(List<LongPoint[]> segmentsUnreadRemoved) {
        this.segmentsUnreadRemoved = segmentsUnreadRemoved;
    }
}
