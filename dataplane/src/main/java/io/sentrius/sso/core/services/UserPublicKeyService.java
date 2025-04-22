package io.sentrius.sso.core.services;

import io.sentrius.sso.core.model.users.UserPublicKey;
import io.sentrius.sso.core.repository.UserPublicKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserPublicKeyService {
    private final UserPublicKeyRepository userPublicKeyRepository;

    @Transactional(readOnly = true)
    public List<UserPublicKey> getPublicKeysForUser(Long userId) {
        return userPublicKeyRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<UserPublicKey> getPublicKeysForHostGroup(Long hostGroup) {
        return userPublicKeyRepository.findByHostGroupId(hostGroup);
    }

    @Transactional(readOnly = true)
    public List<UserPublicKey> getPublicKeysForHostGroup(Long userId, Long hostGroupId) {
        return userPublicKeyRepository.findByUserIdAndHostGroupId(userId, hostGroupId);
    }

    @Transactional
    public UserPublicKey addPublicKey(UserPublicKey userPublicKey) {
        return userPublicKeyRepository.save(userPublicKey);
    }

    @Transactional
    public void deletePublicKey(Long publicKeyId) {
        userPublicKeyRepository.deleteById(publicKeyId);
    }

    @Transactional(readOnly = true)
    public Optional<UserPublicKey> getPublicKeyById(Long id) {
        return userPublicKeyRepository.findById(id);
    }
}
