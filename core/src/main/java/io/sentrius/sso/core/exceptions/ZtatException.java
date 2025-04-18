package io.sentrius.sso.core.exceptions;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.utils.JsonUtil;

public class ZtatException extends Throwable {
    private static final long serialVersionUID = 1L;

    private List<String> mechanisms = new ArrayList<>();
    private String endpoint;
    private String ztatRequestId;

    public ZtatException(String ztatRequired, String endpoint) {
        try {
            ObjectNode node = (ObjectNode) JsonUtil.MAPPER.readTree(ztatRequired);
            if (node.has("message")) {
                JsonNode message = node.get("message");
                ArrayNode mechanism = (ArrayNode) message.get("mechanism");
                mechanisms = new ArrayList<>();
                for (JsonNode m : mechanism) {
                    mechanisms.add(m.asText());
                }
            }
            if (node.has("ztat_request")) {
                // contains an ATAT request
                ztatRequestId = node.get("ztat_request").asText();
            }

        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
        this.endpoint = endpoint;
    }

    public List<String> getMechanisms() {
        return mechanisms;
    }

    public String getEndpoint(){
        return endpoint;
    }

    public String getZtatRequestId(){
        return ztatRequestId;
    }
}
