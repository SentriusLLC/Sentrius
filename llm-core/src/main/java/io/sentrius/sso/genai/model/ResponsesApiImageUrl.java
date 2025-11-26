package io.sentrius.sso.genai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Image URL structure for OpenAI Responses API
 * Contains the URL and detail level for image inputs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponsesApiImageUrl {

    /**
     * The URL of the image (can be a fully qualified URL or base64 data URL)
     */
    @JsonProperty(value = "url")
    private String url;

    /**
     * The detail level for image analysis: "low", "high", or "auto"
     * Defaults to "auto" if not specified
     */
    @JsonProperty(value = "detail")
    @Builder.Default
    private String detail = "auto";
}
