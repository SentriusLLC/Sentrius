package io.sentrius.sso.locator;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class KubernetesAgentLocator {


    public URI resolveWebSocketUri(String host, String sessionId, String chatGroupId, String ztat, String userId) {
        // DNS: sentrius-agent-[ID].[namespace].svc.cluster.local
        ///api/v1/chat/attach/subscribe?sessionId=${encodeURIComponent(this.sessionId)}&chatGroupId=${this.chatGroupId}&ztat=${encodeURIComponent(jwt)
        String fqdn = String.format("%s/api/v1/chat/attach/subscribe?sessionId=%s&chatGroupId=%s&ztat=%s&userId=%s",
            host,  sessionId, chatGroupId, ztat, userId);
        return URI.create(fqdn);
    }
}
