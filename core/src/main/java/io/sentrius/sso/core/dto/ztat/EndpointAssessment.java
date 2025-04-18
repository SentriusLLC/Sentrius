package io.sentrius.sso.core.dto.ztat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ATAT requests are agent requests to approve or deny a request.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EndpointAssessment {
    // don't release any sensitive information
    private String sessionId;
    private String risk;
    private String description;
}
