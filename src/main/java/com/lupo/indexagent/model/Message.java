package com.lupo.indexagent.model;

import java.util.Map;

public class Message {
    private final String pubSubAcknowledgmentId;
    private final Map<String, Object> messageAsMap;

    public Message(final String pubSubAcknowledgmentId,
                   final Map<String, Object> messageAsMap) {
        this.pubSubAcknowledgmentId = pubSubAcknowledgmentId;
        this.messageAsMap = messageAsMap;
    }

    public String getPubSubAcknowledgmentId() {
        return pubSubAcknowledgmentId;
    }

    public Map<String, Object> getMessageAsMap() {
        return messageAsMap;
    }


}
