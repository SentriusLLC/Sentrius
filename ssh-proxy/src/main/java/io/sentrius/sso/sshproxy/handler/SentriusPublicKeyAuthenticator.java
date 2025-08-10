package io.sentrius.sso.sshproxy.handler;

import io.sentrius.sso.core.model.users.UserPublicKey;
import io.sentrius.sso.core.repository.UserPublicKeyRepository;
import io.sentrius.sso.core.repository.UserRepository;
import io.sentrius.sso.core.services.UserPublicKeyService;
import io.sentrius.sso.core.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class SentriusPublicKeyAuthenticator implements PublickeyAuthenticator {

    private final UserService userService;
    private final UserPublicKeyService userPublicKeyService;

    @Override
    public boolean authenticate(String username, PublicKey incomingKey, ServerSession session) {
        log.info("Public key authentication attempt for user: {}", username);

        var user = userService.findByUsername(username);
        if (user.isEmpty()) {
            log.warn("User not found: {}", username);
            return false;
        }

        List<UserPublicKey> keys = userPublicKeyService.getPublicKeysForUser(user.get().getId());

        for (UserPublicKey storedKey : keys) {
            try {
                PublicKey stored = parseOpenSSHKey(storedKey.getPublicKey());
                if (stored.equals(incomingKey)) {
                    log.info("Public key matched for user: {}", username);
                    return true;
                }
            } catch (Exception e) {
                log.warn("Failed to parse stored public key for user {}: {}", username, e.getMessage());
            }
        }

        log.warn("No matching public key found for user: {}", username);
        return false;
    }

    private PublicKey parseOpenSSHKey(String sshKey) throws Exception {
        AuthorizedKeyEntry entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(sshKey);
        return entry.resolvePublicKey(null, null);
    }
}