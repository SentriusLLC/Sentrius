package io.sentrius.sso.genai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void messageBuilderCreatesValidMessage() {
        Message message = Message.builder()
            .role("user")
            .content("Hello, how are you?")
            .refusal(null)
            .build();

        assertNotNull(message);
        assertEquals("user", message.getRole());
        assertEquals("Hello, how are you?", message.getContent());
        assertNull(message.getRefusal());
    }

    @Test
    void messageCanBeCreatedWithNoArgsConstructor() {
        Message message = new Message();
        assertNotNull(message);
        assertNull(message.getRole());
        assertNull(message.getContent());
        assertNull(message.getRefusal());
    }

    @Test
    void messageCanBeCreatedWithAllArgsConstructor() {
        Message message = new Message("assistant", "I'm doing well, thank you!", "");

        assertEquals("assistant", message.getRole());
        assertEquals("I'm doing well, thank you!", message.getContent());
        assertEquals("", message.getRefusal());
    }

    @Test
    void messageSettersAndGettersWork() {
        Message message = new Message();
        
        message.setRole("system");
        message.setContent("You are a helpful assistant");
        message.setRefusal("no refusal");

        assertEquals("system", message.getRole());
        assertEquals("You are a helpful assistant", message.getContent());
        assertEquals("no refusal", message.getRefusal());
    }

    @Test
    void messageHandlesNullValues() {
        Message message = Message.builder()
            .role(null)
            .content(null)
            .refusal(null)
            .build();

        assertNull(message.getRole());
        assertNull(message.getContent());
        assertNull(message.getRefusal());
    }

    @Test
    void messageEqualsAndHashCodeWork() {
        Message message1 = Message.builder()
            .role("user")
            .content("Test content")
            .refusal(null)
            .build();

        Message message2 = Message.builder()
            .role("user")
            .content("Test content")
            .refusal(null)
            .build();

        assertEquals(message1, message2);
        assertEquals(message1.hashCode(), message2.hashCode());
    }

    @Test
    void messageToStringContainsFieldValues() {
        Message message = Message.builder()
            .role("user")
            .content("Hello")
            .build();

        String toString = message.toString();
        
        assertTrue(toString.contains("user"));
        assertTrue(toString.contains("Hello"));
    }
}