package io.sentrius.sso.core.promptadvisor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RefinePromptRequest {
    private String prompt;
    private List<String> recommendations;
    private String explanation;
    private Map<String, Object> context;
}
