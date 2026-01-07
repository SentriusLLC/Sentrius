# AI-Powered Tooltip System

## Overview

The Sentrius AI-Powered Tooltip System provides contextual help and descriptions for UI elements using LLM integration and indexed codebase documentation. Users can right-click on UI elements to get intelligent, context-aware tooltips and have conversational assistance about features.

## Architecture

### Components

1. **Frontend Integration** (`ai-helper.js`)
   - Right-click context menu for element descriptions
   - Chat modal for conversational assistance
   - Automatic element context extraction (tag, ID, classes, attributes, text content)

2. **Backend API** (`TooltipController`)
   - `/api/v1/tooltip/describe` - Get AI description for a UI element
   - `/api/v1/tooltip/chat` - Chat with AI about features and elements
   - `/api/v1/tooltip/admin/index` - Trigger manual codebase indexing (admin only)

3. **Services**
   - **TooltipService** - Orchestrates tooltip generation using document search and LLM
   - **CodebaseIndexingService** - Indexes Java source files and markdown documentation
   - **DocumentService** - Provides semantic search over indexed content
   - **LLMService** - Integrates with OpenAI/LLM providers for text generation

4. **Data Transfer Objects**
   - `TooltipDescribeRequest` / `TooltipDescribeResponse`
   - `TooltipChatRequest` / `TooltipChatResponse`

## How It Works

### Description Flow

1. User right-clicks on a UI element
2. Frontend extracts element context (tag, ID, classes, text, attributes, DOM path)
3. Context is sent to `/api/v1/tooltip/describe` endpoint
4. Backend:
   - Builds search query from element context
   - Searches indexed documentation using semantic search
   - Combines relevant docs with element info
   - Calls LLM to generate concise description (2-3 sentences)
5. Description is displayed in a notification popup

### Chat Flow

1. User opens AI chat modal (via right-click menu or directly)
2. User types a question about a feature or element
3. Message is sent to `/api/v1/tooltip/chat` endpoint
4. Backend:
   - Searches documentation based on the question
   - Optionally includes element context if provided
   - Calls LLM to generate helpful response
5. Response is displayed in chat conversation

### Indexing Flow

1. **Automatic** (on application startup if configured):
   - Scans codebase directory for `.md` and `*Controller.java` / `*Service.java` files
   - Extracts metadata (title, summary, package, class name, JavaDoc)
   - Generates embeddings for semantic search
   - Stores in Document repository

2. **Manual** (via admin endpoint):
   - Admin users can trigger `/api/v1/tooltip/admin/index`
   - Re-indexes codebase on demand
   - Returns statistics (files processed, success/error counts)

## Configuration

### Application Properties

Add to `application.properties` or `application.yml`:

```properties
# Tooltip Configuration
sentrius.tooltip.max-context-documents=5
sentrius.tooltip.similarity-threshold=0.5
sentrius.tooltip.llm-model=gpt-4o-mini

# Indexing Configuration
sentrius.tooltip.index.enabled=true
sentrius.tooltip.index.codebase-path=/path/to/sentrius/codebase

# LLM Endpoint (if not using default)
agent.open.ai.endpoint=http://localhost:8080
```

### Frontend Integration

Include the AI Helper library in your HTML templates:

```html
<script th:inline="javascript">
    var csrf = [[${session._csrf}]];
</script>
<script src="/js/ai-helper.js"></script>
<script>
    // Initialize AI Helper
    const aiHelper = new AIHelper({
        apiEndpoint: '/api/v1/tooltip',
        enableDescriptions: true,
        enableChat: true
    });
</script>
```

## API Endpoints

### POST /api/v1/tooltip/describe

Get AI-powered description for a UI element.

**Request:**
```json
{
  "context": {
    "tagName": "BUTTON",
    "id": "submit-btn",
    "className": "btn btn-primary",
    "textContent": "Submit Form",
    "attributes": {
      "type": "button",
      "aria-label": "Submit the form"
    },
    "path": "body > div.container > form > button#submit-btn"
  },
  "timestamp": 1234567890
}
```

**Response:**
```json
{
  "description": "This button submits the form data to the server. Click it after filling in all required fields to save your changes.",
  "message": "This button submits the form data to the server. Click it after filling in all required fields to save your changes.",
  "success": true
}
```

### POST /api/v1/tooltip/chat

Chat with AI assistant about features and elements.

**Request:**
```json
{
  "message": "How do I configure SSH access policies?",
  "context": null,
  "timestamp": 1234567890
}
```

**Response:**
```json
{
  "response": "To configure SSH access policies in Sentrius, navigate to the Access Policies page. You can define rules based on user attributes, time windows, and resource tags. Policies use ABAC (Attribute-Based Access Control) to enforce zero-trust security.",
  "message": "To configure SSH access policies in Sentrius...",
  "success": true
}
```

### POST /api/v1/tooltip/admin/index

Trigger manual indexing of codebase and documentation (admin only).

**Response:**
```json
{
  "success": true,
  "message": "Indexing completed successfully",
  "totalFiles": 150,
  "successCount": 148,
  "errorCount": 2,
  "errors": [
    "Error indexing docs/broken.md: File not found",
    "Error indexing src/BrokenController.java: Parse error"
  ]
}
```

## Security

- All endpoints require user authentication (CAN_LOG_IN permission)
- Admin indexing endpoint requires CAN_MANAGE_SYSTEMS permission
- CSRF protection via X-CSRF-TOKEN header
- User context is preserved for audit logging

## Performance Considerations

- **Semantic Search**: Uses vector embeddings for fast similarity search
- **Result Limiting**: Configurable `max-context-documents` (default: 5)
- **Similarity Threshold**: Configurable minimum similarity score (default: 0.5)
- **LLM Token Limits**: Descriptions limited to 300 tokens, context limited to first 200 chars per document

## Troubleshooting

### No tooltips appearing
- Check that indexing has been run (`/api/v1/tooltip/admin/index`)
- Verify `sentrius.tooltip.index.enabled=true`
- Ensure LLM service is available and configured

### Poor quality tooltips
- Increase `sentrius.tooltip.max-context-documents` for more context
- Lower `sentrius.tooltip.similarity-threshold` for more results
- Re-index with updated documentation

### Indexing errors
- Verify `sentrius.tooltip.index.codebase-path` is correct and accessible
- Check file permissions for reading source files
- Review error messages in response for specific file issues

## Future Enhancements

- [ ] Real-time indexing on file changes
- [ ] Support for additional file types (XML, YAML, properties)
- [ ] User-specific tooltip customization
- [ ] Tooltip analytics and feedback
- [ ] Caching layer for frequently requested tooltips
- [ ] Multi-language support

## Development

### Adding New Content Types

To index additional content types, extend `CodebaseIndexingService`:

1. Add a new `index[Type]File` method
2. Update `indexCodebase()` to call the new method
3. Add glob patterns to `findFiles()` call

### Customizing LLM Prompts

Edit the prompt templates in `TooltipService`:
- `generateDescription()` - For element descriptions
- `generateChatResponse()` - For chat interactions

### Testing

Run tests:
```bash
mvn test -pl api -Dtest=TooltipControllerTest
```

All tooltip functionality is unit tested with mocked dependencies.

## License

Copyright © 2024 Sentrius LLC. All rights reserved.
