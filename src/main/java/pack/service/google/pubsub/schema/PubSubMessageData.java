package pack.service.google.pubsub.schema;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = false)
public class PubSubMessageData {

    @JsonProperty
    private String emailAddress;

    @JsonProperty
    private String historyId;

    private Map<String, Object> other = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getOtherProperty() {
        return other;
    }

    @JsonAnySetter
    public void setOtherProperty(String name, Object value) {
        other.put(name, value);
    }

    public Map<String, Object> getOtherProperties() {
        return other;
    }

    /////////////////////////


    public String getEmailAddress() {
        return emailAddress;
    }

    public String getHistoryId() {
        return historyId;
    }
}