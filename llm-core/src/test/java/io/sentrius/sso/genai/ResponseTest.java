package io.sentrius.sso.genai;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResponseTest {

    @Test
    void concatenateResponsesReturnsEmptyStringForEmptyOutputItems() {
        Response response = Response.builder()
            .outputItems(Arrays.asList())
            .build();

        String result = response.concatenateResponses();

        assertEquals("", result);
    }

    @Test
    void concatenateResponsesJoinsMultipleTextItems() {
        Response.ContentItem content1 = new Response.ContentItem();
        content1.setType("output_text");
        content1.setText("Hello");
        
        Response.ContentItem content2 = new Response.ContentItem();
        content2.setType("output_text");
        content2.setText(" World");

        Response.OutputItem outputItem = new Response.OutputItem();
        outputItem.setRole("assistant");
        outputItem.setContent(Arrays.asList(content1, content2));

        Response response = Response.builder()
            .outputItems(Arrays.asList(outputItem))
            .build();

        String result = response.concatenateResponses();

        assertEquals("Hello World", result);
    }

    @Test
    void concatenateResponsesHandlesSingleMessage() {
        Response.ContentItem content = new Response.ContentItem();
        content.setType("output_text");
        content.setText("Single message");

        Response.OutputItem outputItem = new Response.OutputItem();
        outputItem.setRole("assistant");
        outputItem.setContent(Arrays.asList(content));

        Response response = Response.builder()
            .outputItems(Arrays.asList(outputItem))
            .build();

        String result = response.concatenateResponses();

        assertEquals("Single message", result);
    }

    @Test
    void responseBuilderCreatesValidObject() {
        Response.Usage usage = new Response.Usage();

        usage.setTotalTokens(30);

        Response response = Response.builder()
            .id("test-id")
            .object("response")
            .created(1234567890L)
            .model("gpt-4o")
            .usage(usage)
            .systemFingerprint("test-fingerprint")
            .build();

        assertNotNull(response);
        assertEquals("test-id", response.getId());
        assertEquals("response", response.getObject());
        assertEquals(1234567890L, response.getCreated());
        assertEquals("gpt-4o", response.getModel());
        assertEquals(usage, response.getUsage());
        assertEquals("test-fingerprint", response.getSystemFingerprint());
    }

    @Test
    void outputItemStoresCorrectData() {
        Response.ContentItem content = new Response.ContentItem();
        content.setType("output_text");
        content.setText("Test content");

        Response.OutputItem outputItem = new Response.OutputItem();
        outputItem.setRole("assistant");
        outputItem.setContent(Arrays.asList(content));

        assertEquals("assistant", outputItem.getRole());
        assertEquals(1, outputItem.getContent().size());
        assertEquals("Test content", outputItem.getContent().get(0).getText());
    }


    @Test
    void responseHandlesNullOutputItemsGracefully() {
        Response response = Response.builder()
            .outputItems(null)
            .build();

        String result = response.concatenateResponses();
        assertEquals("", result);
    }
    
    @Test
    void contentItemStoresTypeAndText() {
        Response.ContentItem content = new Response.ContentItem();
        content.setType("output_text");
        content.setText("Sample text");

        assertEquals("output_text", content.getType());
        assertEquals("Sample text", content.getText());
    }
}