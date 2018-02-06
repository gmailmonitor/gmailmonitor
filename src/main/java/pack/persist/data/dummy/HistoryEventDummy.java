package pack.persist.data.dummy;

import pack.persist.data.HistoryEvent;

// Denotes when data is assembled from a source other than the database - allows use of familiar underlying getter/setters
// The data format resembles what is held in the subclass, but the meaning is different (e.g. data was aggregated)
public class HistoryEventDummy extends HistoryEvent {
}
