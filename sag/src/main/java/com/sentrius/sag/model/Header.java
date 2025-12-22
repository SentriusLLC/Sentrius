package com.sentrius.sag.model;

public class Header {
    private final int version;
    private final String messageId;
    private final String source;
    private final String destination;
    private final long timestamp;
    private final String correlation;
    private final Integer ttl;

    public Header(int version, String messageId, String source, String destination, 
                  long timestamp, String correlation, Integer ttl) {
        this.version = version;
        this.messageId = messageId;
        this.source = source;
        this.destination = destination;
        this.timestamp = timestamp;
        this.correlation = correlation;
        this.ttl = ttl;
    }

    public int getVersion() {
        return version;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getCorrelation() {
        return correlation;
    }

    public Integer getTtl() {
        return ttl;
    }

    @Override
    public String toString() {
        return "Header{version=" + version + ", messageId='" + messageId + "', source='" + source + 
               "', destination='" + destination + "', timestamp=" + timestamp + 
               ", correlation='" + correlation + "', ttl=" + ttl + "}";
    }
}
