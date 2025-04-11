package io.sentrius.sso.core.dto.ztat;
import java.util.List;
import io.sentrius.sso.core.dto.UserDTO;
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
public class AtatRequest {
    // don't release any sensitive information
    private String requestId;
    private List<String> messages;
    private String requestedAction;
}
