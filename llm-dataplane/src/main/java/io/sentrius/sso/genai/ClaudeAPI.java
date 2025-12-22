package io.sentrius.sso.genai;

import io.sentrius.sso.genai.model.ApiEndPointRequest;
import io.sentrius.sso.integrations.exceptions.HttpException;
import io.sentrius.sso.security.TokenProvider;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ClaudeAPI class for interacting with Anthropic's Claude API.
 * 
 * This class extends the basic API functionality to work with Claude's
 * specific authentication and header requirements.
 * 
 * @author Sentrius
 * @version 1.0
 */
@Slf4j
public class ClaudeAPI extends GenerativeAPI {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    
    public ClaudeAPI(TokenProvider authToken, OkHttpClient client) {
        super(authToken, client);
    }

    public ClaudeAPI(TokenProvider authToken) {
        // Claude API often takes longer to respond than OpenAI, especially for complex reasoning tasks.
        // Extended read timeout to 60 seconds to accommodate Claude's response times.
        super(authToken, new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(15))
            .build());
    }

    /**
     * Execute request to Claude API with proper headers.
     * Claude requires:
     * - x-api-key header (instead of Authorization Bearer)
     * - anthropic-version header
     * - content-type: application/json
     * 
     * @param apiRequest Api Request object
     * @return Response body from Claude API
     */
    @Override
    public String sample(final ApiEndPointRequest apiRequest) throws HttpException {
        Objects.requireNonNull(apiRequest);
        log.info("Making request to Claude API: {}", apiRequest.getEndpoint());
        
        String requestBodyJson = buildRequestBody(apiRequest);
        log.info("Claude request body: {}", requestBodyJson);

        RequestBody body = RequestBody.create(requestBodyJson, 
            MediaType.get("application/json; charset=utf-8"));
        
        // Claude uses x-api-key header instead of Authorization Bearer
        Request request = new Request.Builder()
            .url(apiRequest.getEndpoint())
            .header("x-api-key", authToken.getToken())
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("content-type", "application/json")
            .post(body)
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.body() == null) {
                    log.error("Claude API request failed: {}", response.message());
                    throw new HttpException(response.code(), "Claude API request failed");
                } else {
                    String errorBody = response.body().string();
                    log.error("Claude API request failed: {}", errorBody);
                    throw new HttpException(response.code(), errorBody);
                }
            } else {
                String responseBody = response.body().string();
                log.info("Received response from Claude API");
                log.debug("Claude response: {}", responseBody);
                
                // Convert Claude response format to OpenAI-compatible format
                return convertClaudeResponse(responseBody);
            }
        } catch (IOException e) {
            log.error("Claude API request failed: {}", e.getMessage());
            throw new HttpException(500, e.getMessage());
        }
    }

    /**
     * Convert Claude's response format to OpenAI-compatible format.
     * This allows the rest of the system to work with a unified response format.
     */
    private String convertClaudeResponse(String claudeResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            
            // Parse Claude response
            var claudeResponseObj = mapper.readTree(claudeResponse);
            
            // Claude response format:
            // {
            //   "id": "msg_xxx",
            //   "type": "message",
            //   "role": "assistant",
            //   "content": [{"type": "text", "text": "..."}],
            //   "model": "claude-3-...",
            //   "stop_reason": "end_turn",
            //   "usage": {...}
            // }
            
            // Extract the text content
            String content = "";
            if (claudeResponseObj.has("content") && claudeResponseObj.get("content").isArray()) {
                var contentArray = claudeResponseObj.get("content");
                if (contentArray.size() > 0) {
                    var firstContent = contentArray.get(0);
                    if (firstContent.has("text")) {
                        content = firstContent.get("text").asText();
                    }
                }
            }
            
            // Convert to OpenAI format (simplified version matching what the system expects)
            var openAiFormat = mapper.createObjectNode();
            openAiFormat.put("id", claudeResponseObj.has("id") ? claudeResponseObj.get("id").asText() : "");
            openAiFormat.put("object", "chat.completion");
            openAiFormat.put("created", System.currentTimeMillis() / 1000);
            openAiFormat.put("model", claudeResponseObj.has("model") ? claudeResponseObj.get("model").asText() : "claude");
            
            var choices = mapper.createArrayNode();
            var choice = mapper.createObjectNode();
            choice.put("index", 0);
            
            var message = mapper.createObjectNode();
            message.put("role", "assistant");
            message.put("content", content);
            
            choice.set("message", message);
            choice.put("finish_reason", 
                claudeResponseObj.has("stop_reason") ? claudeResponseObj.get("stop_reason").asText() : "stop");
            
            choices.add(choice);
            openAiFormat.set("choices", choices);
            
            // Add usage information if available
            if (claudeResponseObj.has("usage")) {
                openAiFormat.set("usage", claudeResponseObj.get("usage"));
            }
            
            return mapper.writeValueAsString(openAiFormat);
            
        } catch (Exception e) {
            log.warn("Failed to convert Claude response format to OpenAI format. " +
                "Returning original Claude response which may cause compatibility issues downstream. " +
                "Error: {}", e.getMessage());
            return claudeResponse;
        }
    }
}
