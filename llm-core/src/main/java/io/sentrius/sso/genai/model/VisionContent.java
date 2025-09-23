package io.sentrius.sso.genai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents content in a vision message, which can be either text or image.
 * Used for OpenAI Vision API requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VisionContent {
    
    /**
     * Type of content: "text" or "image_url"
     */
    @JsonProperty(value = "type")
    private String type;
    
    /**
     * Text content (when type is "text")
     */
    @JsonProperty(value = "text")
    private String text;
    
    /**
     * Image URL details (when type is "image_url")
     */
    @JsonProperty(value = "image_url")
    private ImageUrl imageUrl;
    
    /**
     * Inner class for image URL details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageUrl {
        /**
         * The URL of the image (can be a URL or base64 encoded data URI)
         */
        @JsonProperty(value = "url")
        private String url;
        
        /**
         * Optional detail level: "low", "high", or "auto"
         */
        @JsonProperty(value = "detail")
        private String detail;
    }
}
