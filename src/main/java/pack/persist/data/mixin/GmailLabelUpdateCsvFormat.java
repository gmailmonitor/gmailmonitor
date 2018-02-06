package pack.persist.data.mixin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

//@JsonPropertyOrder(value = { "year", "messagesTotal", "messagesUnread"}) // Shouldn't use this unless ordering is absolutely needed
@JsonIgnoreProperties(ignoreUnknown = true)
public interface GmailLabelUpdateCsvFormat {
    @JsonProperty("year") abstract long getUpdateTimeMillis(); // rename property
    @JsonProperty("messagesTotal") abstract int getMessagesTotal(); // rename property
    @JsonProperty("messagesUnread") abstract int getMessagesUnread(); // rename property
}


