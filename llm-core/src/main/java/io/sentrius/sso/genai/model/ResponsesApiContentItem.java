package io.sentrius.sso.genai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Content item for OpenAI Responses API
 * Represents a piece of content within a message (text, image, etc.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponsesApiContentItem {
    
    /**
     * Type of content (e.g., "input_text", "input_image", "output_text")
     */
    @JsonProperty(value = "type")
    private String type;
    
    /**
     * Text content (for text types)
     */
    @JsonProperty(value = "text")
    private String text;
    
    /**
     * Image URL object (for image types)
     * Contains the URL and detail level
     */
    @JsonProperty(value = "image_url")
    private ResponsesApiImageUrl imageUrl;

    /**
     * Base64-encoded image (used only when image is provided via base64)
     */
    @JsonProperty("image_base64")
    private String imageBase64;
}

