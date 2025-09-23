package io.sentrius.sso.genai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.sentrius.sso.genai.model.VisionContent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Message class for Vision API that supports both text and image content.
 * Extends the base Message functionality to include multimodal content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VisionMessage {
    
    @JsonProperty(value = "role")
    private String role;
    
    /**
     * Content can be either a simple string or a list of VisionContent objects
     * for multimodal messages (text + images)
     */
    @JsonProperty(value = "content")
    private Object content;
    
    @JsonProperty(value = "refusal")
    private String refusal;
}
