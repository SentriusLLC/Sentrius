# LLM Vision API Integration - Implementation Guide

## Overview

This document describes the complete LLM Vision API integration for analyzing RDP session screenshots in the Sentrius platform.

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    LLM Vision API                           │
│                  (OpenAI GPT-4 Vision)                      │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                  LLMService (core)                          │
│  - analyzeImage(TokenDTO, imageBase64, prompt)             │
│  - analyzeImages(TokenDTO, imagesBase64[], prompt)         │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│         RdpSessionSummarizationAgent (analytics)            │
│  - Scheduled task (every 2 minutes)                         │
│  - Finds unprocessed RDP sessions                           │
│  - Selects up to 5 representative screenshots               │
│  - Calls Vision API for analysis                            │
│  - Stores summary in database                               │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              Database (PostgreSQL)                          │
│  - rdp_session_screenshots (image data)                     │
│  - rdp_session_summaries (AI analysis)                      │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│            Web UI (api/templates)                           │
│  - Tabbed session list (SSH + RDP)                          │
│  - Detailed RDP session view                                │
│  - Screenshot grid with modal viewer                        │
│  - AI analysis display                                      │
└─────────────────────────────────────────────────────────────┘
```

## Vision API Models (llm-core)

### VisionContent
Represents multimodal content (text or image) in a vision message.

```java
VisionContent.builder()
    .type("image_url")
    .imageUrl(VisionContent.ImageUrl.builder()
        .url("data:image/png;base64,...")
        .detail("auto")
        .build())
    .build()
```

### VisionMessage
Message that supports both text and images.

```java
VisionMessage.builder()
    .role("user")
    .content(List.of(textContent, imageContent))
    .build()
```

### VisionRequest
Complete request for vision API.

```java
VisionRequest.builder()
    .model("gpt-4o-mini")
    .messages(List.of(visionMessage))
    .maxTokens(500)
    .build()
```

## LLM Service Methods

### analyzeImage()
Analyze a single image with a text prompt.

```java
String result = llmService.analyzeImage(
    tokenDTO,
    "data:image/png;base64,iVBORw0KGgoAAAANS...",
    "What applications are visible in this screenshot?"
);
```

### analyzeImages()
Analyze multiple images together (batch analysis).

```java
List<String> images = List.of(
    "data:image/png;base64,...",
    "data:image/png;base64,...",
    "data:image/png;base64,..."
);

String result = llmService.analyzeImages(
    tokenDTO,
    images,
    "Analyze these RDP session screenshots and identify activities"
);
```

## RDP Session Analysis

### Configuration
Enable the RDP session summarization agent:

```yaml
agents:
  rdp-session-analytics:
    enabled: true
```

### Analysis Process

1. **Scheduled Execution** - Runs every 2 minutes
2. **Session Discovery** - Finds sessions with unprocessed screenshots
3. **Screenshot Selection** - Selects up to 5 representative screenshots (evenly distributed)
4. **Image Preparation** - Converts byte arrays to base64 data URIs
5. **Vision API Call** - Sends images and prompt to GPT-4 Vision
6. **Result Parsing** - Extracts analysis from JSON response
7. **Storage** - Saves summary to rdp_session_summaries table
8. **Marking Processed** - Updates screenshot processed flags

### Analysis Prompt
```text
Analyze these N screenshots from an RDP session. Provide a detailed analysis including:
1) What applications or activities were visible
2) Any notable user actions or patterns
3) Security-relevant observations (unusual access, sensitive data visible, etc.)
4) Overall session characterization
Keep the analysis concise but comprehensive.
```

## Database Schema

### rdp_session_screenshots
```sql
CREATE TABLE rdp_session_screenshots (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    captured_at TIMESTAMP NOT NULL,
    image_data BYTEA NOT NULL,
    image_format VARCHAR(10),
    file_size BIGINT,
    processed BOOLEAN DEFAULT FALSE,
    analysis_result TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### rdp_session_summaries
```sql
CREATE TABLE rdp_session_summaries (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL UNIQUE,
    user_identifier VARCHAR(255) NOT NULL,
    target_identifier VARCHAR(255),
    session_start TIMESTAMP,
    session_end TIMESTAMP,
    summary TEXT,
    key_activities TEXT,
    risk_indicators TEXT,
    screenshot_count INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);
```

## REST API Endpoints

### List RDP Sessions
```http
GET /api/v1/sessions/rdp/list
Authorization: Required (CAN_MANAGE_SYSTEMS)

Response: [
  {
    "id": 1,
    "sessionId": "rdp-session-123",
    "userIdentifier": "john.doe",
    "targetIdentifier": "server01",
    "sessionStart": "2025-10-07T10:00:00Z",
    "sessionEnd": "2025-10-07T10:30:00Z",
    "screenshotCount": 15,
    "summary": "User accessed financial application..."
  }
]
```

### Get Session Details
```http
GET /api/v1/sessions/rdp/{sessionId}
Authorization: Required (CAN_MANAGE_SYSTEMS)

Response: {
  "id": 1,
  "sessionId": "rdp-session-123",
  ...
}
```

### Get Screenshots
```http
GET /api/v1/sessions/rdp/{sessionId}/screenshots
Authorization: Required (CAN_MANAGE_SYSTEMS)

Response: [
  {
    "id": 1,
    "sessionId": "rdp-session-123",
    "capturedAt": "2025-10-07T10:05:00Z",
    "imageFormat": "png",
    "fileSize": 102400,
    "processed": true
  }
]
```

### Get Screenshot Image
```http
GET /api/v1/sessions/rdp/screenshot/{screenshotId}/image
Authorization: Required (CAN_MANAGE_SYSTEMS)

Response: Binary image data (image/png or image/jpeg)
```

## Web UI

### Session List Page
URL: `/sso/v1/sessions/audit/list`

Features:
- Bootstrap tabs for Terminal Sessions and RDP Sessions
- DataTables with sorting, filtering, pagination
- Real-time data loading via AJAX
- Click-through to detailed views

### RDP Session Detail Page
URL: `/sso/v1/sessions/rdp/view?sessionId={sessionId}`

Features:
- Session metadata display (user, target, times, duration)
- AI-generated analysis summary (monospace, pre-formatted)
- Screenshot grid (responsive, 300px min width)
- Modal viewer for full-size screenshots
- Click any screenshot thumbnail to view full size

## Security

### Authorization
All RDP session endpoints require:
- Authentication (valid session)
- CAN_MANAGE_SYSTEMS permission

### Data Protection
- Screenshot data stored as BYTEA in PostgreSQL
- Base64 encoding for API transmission
- No persistent storage of base64 strings
- Analysis results stored separately from images

## Performance Considerations

### Screenshot Selection
- Maximum 5 screenshots per analysis to limit API costs
- Evenly distributed across session timeline
- Skips very small images (< 10KB)

### Image Processing
- Lazy loading of image_data column (FetchType.LAZY)
- Images served directly from database via streaming
- No temporary file storage required

### API Rate Limiting
- Scheduled agent runs every 2 minutes
- Processes one session at a time
- Graceful handling of API failures
- Non-blocking - failures don't stop other sessions

## Configuration

### Required Settings

```yaml
# Enable RDP session analytics
agents:
  rdp-session-analytics:
    enabled: true

# OpenAI endpoint (via integration proxy)
agent:
  open:
    ai:
      endpoint: http://localhost:8080

# OpenAI API key (stored in integration_security_token table)
# Connection type: "openai"
```

### Optional Settings

```yaml
# Screenshot capture interval (in RDP proxy)
rdp:
  screenshot:
    interval: 30000  # milliseconds

# Analysis batch size
rdp:
  analysis:
    max-screenshots: 5
```

## Testing

### Unit Tests
```bash
# Test LLM core models
mvn test -pl llm-core

# Test analytics agent
mvn test -pl analytics

# Test API endpoints
mvn test -pl api
```

### Manual Testing

1. **Create RDP Session**
   - Connect to host system via RDP
   - Use system for 2-3 minutes
   - Disconnect

2. **Wait for Analysis**
   - Agent runs every 2 minutes
   - Check logs for "Processing RDP session"

3. **View Results**
   - Navigate to `/sso/v1/sessions/audit/list`
   - Click "RDP Sessions" tab
   - Click "View Details" for session

## Troubleshooting

### No Analysis Generated
- Check OpenAI integration is configured
- Verify API key is valid
- Check agent is enabled in configuration
- Review analytics logs for errors

### Screenshots Not Appearing
- Verify RDP proxy is capturing screenshots
- Check database for image_data records
- Ensure screenshots > 10KB in size

### Vision API Errors
- Check API rate limits
- Verify image format (PNG/JPEG supported)
- Ensure base64 encoding is correct
- Review max_tokens setting (500 default)

## Future Enhancements

### Potential Improvements
1. Real-time analysis (via webhooks)
2. Custom analysis prompts per organization
3. Integration with security incident response
4. Automatic risk scoring
5. Thumbnail generation for faster loading
6. Video compilation from screenshots
7. OCR for text extraction
8. Activity timeline visualization

## References

- OpenAI Vision API: https://platform.openai.com/docs/guides/vision
- Guacamole Protocol: https://guacamole.apache.org/doc/gug/guacamole-protocol.html
- DataTables: https://datatables.net/
- Bootstrap 5: https://getbootstrap.com/docs/5.0/

## Support

For issues or questions:
1. Check application logs in analytics module
2. Verify database schema matches V27 migration
3. Test Vision API connectivity independently
4. Review OpenAI integration configuration

---
Last Updated: 2025-10-07
Version: 1.0.0
