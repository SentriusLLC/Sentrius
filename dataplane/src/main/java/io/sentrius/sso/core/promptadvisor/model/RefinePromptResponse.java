package io.sentrius.sso.core.promptadvisor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class RefinePromptResponse {
    @JsonProperty("original_prompt")
    private String originalPrompt;
    
    @JsonProperty("refined_prompt")
    private String refinedPrompt;
    
    private Integer score;
    private Map<String, Integer> ratings;
    private String explanation;
    private List<String> recommendations;
}
