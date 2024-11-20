package io.sentrius.sso.core.model.sessions;

import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.users.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SessionIdentifier {

    Long sessionId;

    Long instanceId;

    String username;

    String host;

    Integer port;

    public static SessionIdentifier from(User user, SessionOutput sessionOutput) {
        return SessionIdentifier.builder()
            .sessionId(sessionOutput.getSessionId())
            .instanceId(Long.valueOf(sessionOutput.getSessionId()))
            .username(user.getUsername())
            .host(sessionOutput.getConnectedSystem().getHostSystem().getHost())
            .port(sessionOutput.getConnectedSystem().getHostSystem().getPort())
            .build();
    }

    public static SessionIdentifier from(
        ConnectedSystem connectedSystem) {
        return SessionIdentifier.builder()
            .sessionId(connectedSystem.getSession().getId())
            .instanceId(connectedSystem.getSession().getId())
            .username(connectedSystem.getUser().getUsername())
            .host(connectedSystem.getHostSystem().getHost())
            .port(connectedSystem.getHostSystem().getPort())
            .build();
    }
}