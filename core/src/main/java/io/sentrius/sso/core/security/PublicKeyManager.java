package io.sentrius.sso.core.security;


import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PublicKeyManager {

    final Set<String> keys = new LinkedHashSet<>();

    public PublicKeyManager() {
    }

    public void loadFromExisting(String existingKeyFileContent) {
        String[] lines = existingKeyFileContent.split("\n");
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                keys.add(normalize(line));
            }
        }
    }

    public void addKeys(List<String> newKeys) {
        for (String key : newKeys) {
            if (key != null && !key.trim().isEmpty()) {
                keys.add(normalize(key));
            }
        }
    }

    public void addKey(String key) {
        if (key != null && !key.trim().isEmpty()) {
            keys.add(normalize(key));
        }
    }

    public String buildAuthorizedKeysFile() {
        return String.join("\n", keys).trim();
    }

    private String normalize(String key) {
        return key.replace("\n", "").trim();
    }

    public boolean isChangedComparedTo(String existingKeyFileContent) {
        PublicKeyManager other = new PublicKeyManager();
        other.loadFromExisting(existingKeyFileContent);
        return !this.keys.equals(other.keys);
    }
}
