package pack.frontend.databind;

import java.util.List;

public class ResultsContainerMessageData {


    private List<DataPointMessageCount> dataSeriesMessageCounts;
    private DataSeriesLabelChangeSegments dataSeriesLabelChangeSegments;


    public String describeCountData() {
        return "Total and unread count series number of points: " + dataSeriesMessageCounts.size();
    }

    public String describeDeltaData() {
        return
        "Message count deltas -" +
                ", totalAdded: " + dataSeriesLabelChangeSegments.getSegmentsMessagesAdded() +
                ", totalRemoved: " + dataSeriesLabelChangeSegments.getSegmentsMessagesRemoved() +
                ", unreadAdded: " + dataSeriesLabelChangeSegments.getSegmentsUnreadAdded() +
                ", unreadRemoved: " + dataSeriesLabelChangeSegments.getSegmentsUnreadRemoved();
    }

    public void setDataSeriesMessageCounts(List<DataPointMessageCount> dataSeriesMessageCounts) {
        this.dataSeriesMessageCounts = dataSeriesMessageCounts;
    }

    public List<DataPointMessageCount> getDataSeriesMessageCounts() {
        return dataSeriesMessageCounts;
    }

    public void setDataSeriesLabelChangeSegments(DataSeriesLabelChangeSegments dataSeriesLabelChangeSegments) {
        this.dataSeriesLabelChangeSegments = dataSeriesLabelChangeSegments;
    }

    public DataSeriesLabelChangeSegments getDataSeriesLabelChangeSegments() {
        return dataSeriesLabelChangeSegments;
    }
}
