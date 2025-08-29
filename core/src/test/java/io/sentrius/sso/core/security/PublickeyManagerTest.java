package io.sentrius.sso.core.security;


import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PublickeyManagerTest {

    @Test
    void loadFromExisting_shouldAddKeysFromNonEmptyFileContent() {
        PublicKeyManager manager = new PublicKeyManager();
        String existingKeys = "key1\nkey2\nkey3";

        manager.loadFromExisting(existingKeys);

        assertEquals(3, manager.keys.size());
        assertTrue(manager.keys.contains("key1"));
        assertTrue(manager.keys.contains("key2"));
        assertTrue(manager.keys.contains("key3"));
    }

    @Test
    void loadFromExisting_shouldIgnoreEmptyLines() {
        PublicKeyManager manager = new PublicKeyManager();
        String existingKeys = "key1\n\nkey2\n   \nkey3";

        manager.loadFromExisting(existingKeys);

        assertEquals(3, manager.keys.size());
        assertTrue(manager.keys.contains("key1"));
        assertTrue(manager.keys.contains("key2"));
        assertTrue(manager.keys.contains("key3"));
    }

    @Test
    void addKeys_shouldAddValidKeysOnly() {
        PublicKeyManager manager = new PublicKeyManager();
        List<String> newKeys = List.of("key1", "  ", "key2");

        manager.addKeys(newKeys);

        assertEquals(2, manager.keys.size());
        assertTrue(manager.keys.contains("key1"));
        assertTrue(manager.keys.contains("key2"));
    }

    @Test
    void addKey_shouldAddSingleValidKey() {
        PublicKeyManager manager = new PublicKeyManager();

        manager.addKey("key1");

        assertEquals(1, manager.keys.size());
        assertTrue(manager.keys.contains("key1"));
    }

    @Test
    void addKey_shouldIgnoreNullOrEmptyKey() {
        PublicKeyManager manager = new PublicKeyManager();

        manager.addKey(null);
        manager.addKey("  ");

        assertTrue(manager.keys.isEmpty());
    }

    @Test
    void buildAuthorizedKeysFile_shouldReturnKeysAsSingleString() {
        PublicKeyManager manager = new PublicKeyManager();
        manager.addKeys(List.of("key1", "key2", "key3"));

        String result = manager.buildAuthorizedKeysFile();

        assertEquals("key1\nkey2\nkey3", result);
    }

    @Test
    void isChangedComparedTo_shouldReturnTrueIfKeysAreDifferent() {
        PublicKeyManager manager = new PublicKeyManager();
        manager.addKeys(List.of("key1", "key2"));

        String existingKeys = "key1\nkey3";

        assertTrue(manager.isChangedComparedTo(existingKeys));
    }

    @Test
    void isChangedComparedTo_shouldReturnFalseIfKeysAreSame() {
        PublicKeyManager manager = new PublicKeyManager();
        manager.addKeys(List.of("key1", "key2"));

        String existingKeys = "key1\nkey2";

        assertFalse(manager.isChangedComparedTo(existingKeys));
    }
}