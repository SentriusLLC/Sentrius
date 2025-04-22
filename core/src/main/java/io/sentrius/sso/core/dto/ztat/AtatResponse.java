package io.sentrius.sso.core.dto.ztat;
import java.util.List;
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
public class AtatResponse {
    // don't release any sensitive information
    private String requestId; // atat request ID
    private AtatResponseEnum response;
    private String requestForMoreInfoQuestion;
}
