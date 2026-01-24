package io.sentrius.agent.analysis.agents.sessions;

import com.fasterxml.jackson.databind.JsonNode;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.model.sessions.RdpSessionScreenshot;
import io.sentrius.sso.core.model.sessions.RdpSessionSummary;
import io.sentrius.sso.core.repository.RdpSessionScreenshotRepository;
import io.sentrius.sso.core.repository.RdpSessionSummaryRepository;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.agents.AgentExecutionAuditService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analytics agent that processes RDP session screenshots and generates summaries using LLM.
 * Runs on a scheduled task to analyze unprocessed sessions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agents.rdp-session-analytics.enabled", havingValue = "true", matchIfMissing = false)
public class RdpSessionSummarizationAgent {
    
    private final RdpSessionScreenshotRepository screenshotRepository;
    private final RdpSessionSummaryRepository summaryRepository;
    private final IntegrationSecurityTokenService integrationSecurityTokenService;
    private final LLMService llmService;
    private final AgentExecutionAuditService auditService;

    /**
     * Process RDP sessions with unprocessed screenshots every 2 minutes
     */
    @Scheduled(fixedDelay = 120000) // 2 minutes
    @Transactional
    public void processRdpSessions() {
        // Check if OpenAI integration is available
        if (!isLLMAvailable()) {
            log.debug("LLM integration not available, skipping RDP session summarization");
            return;
        }

        log.debug("Checking for RDP sessions with unprocessed screenshots...");

        // Find sessions with unprocessed screenshots
        List<String> sessionIds = screenshotRepository.findSessionsWithUnprocessedScreenshots();

        // Skip audit creation if there's nothing to process
        if (sessionIds.isEmpty()) {
            log.debug("No RDP sessions to process");
            return;
        }

        // Only create audit when we have actual work to do
        String taskExecutionId = UUID.randomUUID().toString();
        createTaskAudit(taskExecutionId, "rdp-session-summarizer");

        String taskStatus = "COMPLETED";
        log.info("Processing {} RDP sessions with unprocessed screenshots...", sessionIds.size());

        try {

            int failed = 0;
            for (String sessionId : sessionIds) {
                try {
                    processSession(sessionId);
                } catch (Exception e) {
                    log.error("Error processing RDP session {}: {}", sessionId, e.getMessage(), e);
                    failed++;
                }
            }

            log.info("Finished processing RDP sessions");

            if (failed > 0) {
                taskStatus = "COMPLETED_WITH_ERRORS";
            }
        } catch (Exception e) {
            log.error("Error in processRdpSessions", e);
            taskStatus = "ERROR";
        } finally {
            closeTaskAudit(taskExecutionId, taskStatus);
        }
    }
    
    /**
     * Process a single RDP session - analyze screenshots and generate summary
     */
    private void processSession(String sessionId) {
        log.info("Processing RDP session: {}", sessionId);
        
        // Get all screenshots for this session
        List<RdpSessionScreenshot> screenshots = screenshotRepository
            .findBySessionIdOrderByCapturedAtAsc(sessionId);
        
        if (screenshots.isEmpty()) {
            log.warn("No screenshots found for session: {}", sessionId);
            return;
        }
        
        // Filter unprocessed screenshots
        List<RdpSessionScreenshot> unprocessed = screenshots.stream()
            .filter(s -> !s.getProcessed())
            .collect(Collectors.toList());
        
        if (unprocessed.isEmpty()) {
            log.info("All screenshots already processed for session: {}", sessionId);
            return;
        }
        
        log.info("Analyzing {} screenshots for session: {}", unprocessed.size(), sessionId);
        
        // Analyze screenshots using LLM
        String sessionAnalysis = analyzeScreenshots(sessionId, unprocessed);
        
        // Get or create session summary
        RdpSessionSummary summary = summaryRepository.findBySessionId(sessionId)
            .orElse(new RdpSessionSummary());
        
        // Update summary with new analysis
        updateSummary(summary, sessionId, screenshots, sessionAnalysis);
        
        // Save summary
        summaryRepository.save(summary);
        
        // Mark screenshots as processed
        for (RdpSessionScreenshot screenshot : unprocessed) {
            screenshot.setProcessed(true);
            screenshot.setAnalysisResult("Included in session summary");
        }
        screenshotRepository.saveAll(unprocessed);
        
        log.info("Successfully processed session {}: {} screenshots analyzed", 
            sessionId, unprocessed.size());
    }
    
    /**
     * Analyze screenshots using LLM vision capabilities
     */
    private String analyzeScreenshots(String sessionId, List<RdpSessionScreenshot> screenshots) {
        try {
            // Build analysis summary from screenshot metadata
            StringBuilder analysisBuilder = new StringBuilder();
            analysisBuilder.append("RDP Session Analysis Summary\n");
            analysisBuilder.append("=============================\n\n");
            analysisBuilder.append("Session ID: ").append(sessionId).append("\n");
            analysisBuilder.append("Number of screenshots captured: ").append(screenshots.size()).append("\n\n");
            
            analysisBuilder.append("Session Timeline:\n");
            analysisBuilder.append("-----------------\n");
            
            Instant sessionStart = screenshots.get(0).getCapturedAt();
            Instant sessionEnd = screenshots.get(screenshots.size() - 1).getCapturedAt();
            long durationSeconds = sessionEnd.getEpochSecond() - sessionStart.getEpochSecond();
            
            analysisBuilder.append(String.format("Start: %s\n", sessionStart));
            analysisBuilder.append(String.format("End: %s\n", sessionEnd));
            analysisBuilder.append(String.format("Duration: %d seconds (%.2f minutes)\n\n", 
                durationSeconds, durationSeconds / 60.0));
            
            // Try to get LLM-based analysis if available
            String llmAnalysis = getLLMAnalysis(screenshots);
            if (llmAnalysis != null && !llmAnalysis.isEmpty()) {
                analysisBuilder.append("AI-Generated Analysis:\n");
                analysisBuilder.append("----------------------\n");
                analysisBuilder.append(llmAnalysis).append("\n\n");
            }
            
            analysisBuilder.append("Screenshot Details:\n");
            analysisBuilder.append("-------------------\n");
            for (int i = 0; i < screenshots.size(); i++) {
                RdpSessionScreenshot screenshot = screenshots.get(i);
                long secondsFromStart = screenshot.getCapturedAt().getEpochSecond() - sessionStart.getEpochSecond();
                
                analysisBuilder.append(String.format("%d. Time: +%ds | Size: %d bytes | Format: %s\n",
                    i + 1,
                    secondsFromStart,
                    screenshot.getFileSize() != null ? screenshot.getFileSize() : 0,
                    screenshot.getImageFormat()));
                
                // Add basic image analysis
                String imageAnalysis = analyzeImage(screenshot);
                if (imageAnalysis != null && !imageAnalysis.isEmpty()) {
                    analysisBuilder.append("   Analysis: ").append(imageAnalysis).append("\n");
                }
            }
            
            // Add summary section
            analysisBuilder.append("\nSession Summary:\n");
            analysisBuilder.append("----------------\n");
            analysisBuilder.append(String.format("This RDP session lasted %.2f minutes with %d screenshots captured.\n",
                durationSeconds / 60.0, screenshots.size()));
            
            if (screenshots.size() < 5) {
                analysisBuilder.append("Note: Limited screenshot data available for comprehensive analysis.\n");
            }
            
            return analysisBuilder.toString();
            
        } catch (Exception e) {
            log.error("Error analyzing screenshots for session: {}", sessionId, e);
            return "Error during analysis: " + e.getMessage();
        }
    }
    
    /**
     * Get LLM-based analysis of screenshots using Vision API
     */
    private String getLLMAnalysis(List<RdpSessionScreenshot> screenshots) {
        try {
            // Get a token for LLM service
            var token = integrationSecurityTokenService.selectToken("openai")
                .orElse(null);
            if (token == null) {
                log.debug("No OpenAI token available for vision analysis");
                return null;
            }
            
            // Create a TokenDTO for the LLM service
            // Note: In production, this should use proper ZTAT token and communication ID
            TokenDTO tokenDTO = TokenDTO.builder()
                .ztatToken("")  // Would need actual ZTAT token in production
                .communicationId(UUID.randomUUID().toString())
                .build();
            
            // Select up to 4 representative screenshots (reduced from 6 for better quality analysis)
            // With full frame capture logic, fewer but higher quality screenshots are better
            List<RdpSessionScreenshot> selectedScreenshots = selectRepresentativeScreenshots(screenshots, 4);
            
            if (selectedScreenshots.isEmpty()) {
                return null;
            }
            
            // Analyze images in batches of 2 to avoid overwhelming the API
            // and to provide better context for each analysis
            StringBuilder fullAnalysis = new StringBuilder();
            int batchSize = 2;
            int totalBatches = (int) Math.ceil((double) selectedScreenshots.size() / batchSize);
            
            for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
                int startIdx = batchIndex * batchSize;
                int endIdx = Math.min(startIdx + batchSize, selectedScreenshots.size());
                List<RdpSessionScreenshot> batch = selectedScreenshots.subList(startIdx, endIdx);
                
                // Convert screenshots to base64
                List<String> imagesBase64 = new ArrayList<>();
                for (RdpSessionScreenshot screenshot : batch) {
                    if (screenshot.getImageData() != null && screenshot.getImageData().length > 0) {
                        String base64 = Base64.getEncoder().encodeToString(screenshot.getImageData());
                        String dataUri = "data:image/" + screenshot.getImageFormat().toLowerCase() + ";base64," + base64;
                        imagesBase64.add(dataUri);
                    }
                }
                
                if (imagesBase64.isEmpty()) {
                    continue;
                }
                
                // Build context-aware prompt
                String prompt;
                if (batchIndex == 0) {
                    // First batch - initial analysis
                    prompt = String.format(
                        "Analyze these images from the beginning of an RDP session. " +
                        "Describe what applications or activities are visible, any notable actions, and initial observations. " +
                        "Be concise and focus on key details. Do not reference image numbers or positions.",
                        imagesBase64.size(), startIdx + 1, endIdx, selectedScreenshots.size()
                    );
                } else if (batchIndex == totalBatches - 1) {
                    // Last batch - final analysis with previous context
                    prompt = String.format(
                        "Analyze these final images from an RDP session. " +
                        "Previous context: %s\n\n" +
                        "Describe what happens in these final images and provide an overall summary of the entire session, " +
                        "including any security-relevant observations. Focus on the activities and applications used, not the images themselves.",
                        imagesBase64.size(), startIdx + 1, endIdx, selectedScreenshots.size(),
                        fullAnalysis.toString().substring(0, Math.min(500, fullAnalysis.length()))
                    );
                } else {
                    // Middle batch - continuation with context
                    prompt = String.format(
                        "Analyze these images from the middle of an RDP session. " +
                        "Previous context: %s\n\n" +
                        "Describe what happens in these images and how the session progresses. Focus on activities, not image references.",
                        imagesBase64.size(), startIdx + 1, endIdx, selectedScreenshots.size(),
                        fullAnalysis.toString().substring(0, Math.min(300, fullAnalysis.length()))
                    );
                }
                
                try {
                    // Call LLM Vision API for this batch
                    String response = llmService.analyzeImages(tokenDTO, imagesBase64, prompt);
                    
                    // Parse the response to extract the analysis text
                    JsonNode jsonResponse = JsonUtil.MAPPER.readTree(response);
                    fullAnalysis.append(extractResponseText(jsonResponse));
                    
                    // Small delay between batches to avoid rate limiting
                    if (batchIndex < totalBatches - 1) {
                        Thread.sleep(1000);
                    }
                    
                } catch (io.sentrius.sso.core.exceptions.ZtatException | com.fasterxml.jackson.core.JsonProcessingException e) {
                    log.warn("LLM Vision API call failed for batch {}: {}", batchIndex + 1, e.getMessage());
                    // Continue with next batch even if one fails
                }
            }
            
            return fullAnalysis.length() > 0 ? fullAnalysis.toString() : null;
            
        } catch (Exception e) {
            log.warn("Failed to get LLM analysis: {}", e.getMessage());
            return null;
        }
    }

    private String extractResponseText(JsonNode response) {
        JsonNode output = response.get("output");
        if (output != null && output.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : output) {
                if ("message".equals(item.path("type").asText())) {
                    for (JsonNode content : item.path("content")) {
                        if ("output_text".equals(content.path("type").asText())) {
                            sb.append(content.path("text").asText()).append("\n");
                        }
                    }
                }
            }
            return sb.toString().trim();
        }
        return "";
    }
    
    /**
     * Select representative screenshots from the session (evenly distributed)
     * Prioritizes larger screenshots which are more likely to be full frames
     */
    private List<RdpSessionScreenshot> selectRepresentativeScreenshots(List<RdpSessionScreenshot> screenshots, int maxCount) {
        if (screenshots.size() <= maxCount) {
            return screenshots;
        }
        
        // Sort by file size descending to prioritize full frames over deltas
        List<RdpSessionScreenshot> sortedBySize = new ArrayList<>(screenshots);
        sortedBySize.sort((a, b) -> Long.compare(
            b.getFileSize() != null ? b.getFileSize() : 0,
            a.getFileSize() != null ? a.getFileSize() : 0
        ));
        
        // Take the largest screenshots (likely full frames)
        // But ensure they're distributed across the session timeline
        List<RdpSessionScreenshot> candidates = sortedBySize.stream()
            .limit(maxCount * 2)  // Take top 2x candidates
            .toList();
        
        // Sort candidates by captured time to maintain chronological order
        List<RdpSessionScreenshot> selected = new ArrayList<>(candidates);
        selected.sort((a, b) -> a.getCapturedAt().compareTo(b.getCapturedAt()));
        
        // Select evenly distributed from the candidates
        List<RdpSessionScreenshot> result = new ArrayList<>();
        int step = Math.max(1, selected.size() / maxCount);
        
        for (int i = 0; i < maxCount && i * step < selected.size(); i++) {
            result.add(selected.get(i * step));
        }
        
        log.debug("Selected {} screenshots from {} total, prioritizing larger (full frame) images", 
            result.size(), screenshots.size());
        
        return result;
    }
    
    /**
     * Analyze a single screenshot image
     */
    private String analyzeImage(RdpSessionScreenshot screenshot) {
        try {
            if (screenshot.getImageData() == null || screenshot.getImageData().length == 0) {
                log.warn("Screenshot has no image data: {}", screenshot.getId());
                return "No image data available";
            }
            
            // Read the image from byte array
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(screenshot.getImageData()));
            if (image == null) {
                return "Unable to read image data";
            }
            
            // Basic image analysis - detect predominant colors, content patterns, etc.
            // This is a simplified version - in production you'd use vision API
            StringBuilder analysis = new StringBuilder();
            analysis.append("Image dimensions: ").append(image.getWidth()).append("x").append(image.getHeight());
            
            // Sample pixels to detect predominant colors
            Map<String, Integer> colorCounts = new HashMap<>();
            int sampleRate = 50; // Sample every 50th pixel
            for (int y = 0; y < image.getHeight(); y += sampleRate) {
                for (int x = 0; x < image.getWidth(); x += sampleRate) {
                    int rgb = image.getRGB(x, y);
                    String colorCategory = categorizeColor(rgb);
                    colorCounts.put(colorCategory, colorCounts.getOrDefault(colorCategory, 0) + 1);
                }
            }
            
            // Find dominant color
            String dominantColor = colorCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
            
            analysis.append(", Dominant color: ").append(dominantColor);
            
            return analysis.toString();
            
        } catch (Exception e) {
            log.error("Error analyzing image data for screenshot: {}", screenshot.getId(), e);
            return "Error analyzing image: " + e.getMessage();
        }
    }
    
    /**
     * Categorize RGB color into broad categories
     */
    private String categorizeColor(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        
        // Simple color categorization
        if (red < 50 && green < 50 && blue < 50) return "dark";
        if (red > 200 && green > 200 && blue > 200) return "light";
        if (red > green && red > blue) return "red";
        if (green > red && green > blue) return "green";
        if (blue > red && blue > green) return "blue";
        
        return "mixed";
    }
    
    /**
     * Update session summary with new analysis
     */
    private void updateSummary(RdpSessionSummary summary, String sessionId, 
                              List<RdpSessionScreenshot> allScreenshots, String newAnalysis) {
        if (summary.getSessionId() == null) {
            // New summary
            summary.setSessionId(sessionId);
            summary.setSessionStart(allScreenshots.get(0).getCapturedAt());
        }
        
        // Update session end to latest screenshot
        summary.setSessionEnd(allScreenshots.get(allScreenshots.size() - 1).getCapturedAt());
        
        // Update screenshot count
        summary.setScreenshotCount(allScreenshots.size());
        
        // Append or update summary
        if (summary.getSummary() == null || summary.getSummary().isEmpty()) {
            summary.setSummary(newAnalysis);
        } else {
            // Append new analysis to existing summary
            summary.setSummary(summary.getSummary() + "\n\n--- Updated Analysis ---\n" + newAnalysis);
        }
        
        // Extract user and target from session ID if available
        if (summary.getUserIdentifier() == null) {
            summary.setUserIdentifier("unknown"); // Would be extracted from session metadata
        }
        if (summary.getTargetIdentifier() == null) {
            summary.setTargetIdentifier("unknown"); // Would be extracted from session metadata
        }
    }
    
    /**
     * Check if LLM integration is available
     */
    private boolean isLLMAvailable() {
        try {
            var token = integrationSecurityTokenService.findByConnectionType("openai")
                .stream().findFirst().orElse(null);
            return token != null;
        } catch (Exception e) {
            log.debug("Error checking LLM availability", e);
            return false;
        }
    }

    /**
     * Create an audit record for a scheduled task execution
     */
    private void createTaskAudit(String taskExecutionId, String agentType) {
        try {
            auditService.createAudit(
                "analytics-agent",
                taskExecutionId,
                agentType,
                "system"
            );
            log.debug("Created audit for {} task: {}", agentType, taskExecutionId);
        } catch (Exception e) {
            log.debug("Could not create audit for {} task: {}", agentType, e.getMessage());
        }
    }

    /**
     * Close an audit record for a scheduled task execution
     */
    private void closeTaskAudit(String taskExecutionId, String status) {
        try {
            auditService.closeAudit(taskExecutionId, status);
            log.debug("Closed audit for task {} with status: {}", taskExecutionId, status);
        } catch (Exception e) {
            log.debug("Could not close audit for task: {}", e.getMessage());
        }
    }
}
