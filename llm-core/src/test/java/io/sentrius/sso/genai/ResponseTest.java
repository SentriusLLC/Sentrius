package io.sentrius.sso.genai;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResponseTest {

    @Test
    void concatenateResponsesReturnsEmptyStringForEmptyChoices() {
        Response response = Response.builder()
            .choices(Arrays.asList())
            .build();

        String result = response.concatenateResponses();

        assertEquals("", result);
    }

    @Test
    void concatenateResponsesJoinsMultipleMessages() {
        Message message1 = Message.builder()
            .content("Hello")
            .build();
        
        Message message2 = Message.builder()
            .content(" World")
            .build();

        Response.Choice choice1 = new Response.Choice();
        choice1.setMessage(message1);
        
        Response.Choice choice2 = new Response.Choice();
        choice2.setMessage(message2);

        Response response = Response.builder()
            .choices(Arrays.asList(choice1, choice2))
            .build();

        String result = response.concatenateResponses();

        assertEquals("Hello World", result);
    }

    @Test
    void concatenateResponsesHandlesSingleMessage() {
        Message message = Message.builder()
            .content("Single message")
            .build();

        Response.Choice choice = new Response.Choice();
        choice.setMessage(message);

        Response response = Response.builder()
            .choices(Arrays.asList(choice))
            .build();

        String result = response.concatenateResponses();

        assertEquals("Single message", result);
    }

    @Test
    void responseBuilderCreatesValidObject() {
        Response.Usage usage = new Response.Usage();
        usage.setPromptTokens(10);
        usage.setCompletionTokens(20);
        usage.setTotalTokens(30);

        Response response = Response.builder()
            .id("test-id")
            .object("chat.completion")
            .created(1234567890L)
            .model("gpt-3.5-turbo")
            .usage(usage)
            .systemFingerprint("test-fingerprint")
            .build();

        assertNotNull(response);
        assertEquals("test-id", response.getId());
        assertEquals("chat.completion", response.getObject());
        assertEquals(1234567890L, response.getCreated());
        assertEquals("gpt-3.5-turbo", response.getModel());
        assertEquals(usage, response.getUsage());
        assertEquals("test-fingerprint", response.getSystemFingerprint());
    }

    @Test
    void choiceObjectStoresCorrectData() {
        Message message = Message.builder()
            .content("Test content")
            .role("assistant")
            .build();

        Response.Choice choice = new Response.Choice();
        choice.setIndex(0);
        choice.setMessage(message);
        choice.setFinishReason("stop");
        choice.setLogprobs(null);

        assertEquals(0, choice.getIndex());
        assertEquals(message, choice.getMessage());
        assertEquals("stop", choice.getFinishReason());
        assertNull(choice.getLogprobs());
    }

    @Test
    void usageObjectStoresTokenCounts() {
        Response.Usage usage = new Response.Usage();
        usage.setPromptTokens(15);
        usage.setCompletionTokens(25);
        usage.setTotalTokens(40);

        assertEquals(15, usage.getPromptTokens());
        assertEquals(25, usage.getCompletionTokens());
        assertEquals(40, usage.getTotalTokens());
    }

    @Test
    void responseHandlesNullChoicesGracefully() {
        Response response = Response.builder()
            .choices(null)
            .build();

        assertThrows(NullPointerException.class, () -> response.concatenateResponses());
    }
}