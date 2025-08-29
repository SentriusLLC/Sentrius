package io.sentrius.sso.core.dto.ztat;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Getter
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Slf4j
public class EndpointRequest {
    private String name;
    @Builder.Default
    private List<String> endpoints = new ArrayList<>();

    public void addEndpoint(String endpoint) {
        if (endpoint != null && !endpoint.isEmpty()) {
            endpoints.add(endpoint);
        }
    }

    public String toString(){
        try {
            return JsonUtil.MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean contains(String endpoint) {
        for(String e : endpoints) {
            log.info("Checking if {} starts with {}", e, endpoint);
            if (endpoint.startsWith(e)) {
                return true;
            }
        }
        return false;
    }
}
