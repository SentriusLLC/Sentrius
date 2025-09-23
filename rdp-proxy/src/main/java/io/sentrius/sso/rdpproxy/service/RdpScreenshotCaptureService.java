package io.sentrius.sso.rdpproxy.service;

import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.sessions.RdpSessionScreenshot;
import io.sentrius.sso.core.repository.RdpSessionScreenshotRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service to capture RDP session screenshots from Guacamole protocol stream for analysis.
 * This service intercepts PNG/IMG instructions from the Guacamole protocol and stores them
 * directly in the database for asynchronous processing by the analytics agent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RdpScreenshotCaptureService {

    private final RdpSessionScreenshotRepository screenshotRepository;

    /* ---------- Configuration ---------- */

    @Value("${rdp.screenshot.enabled:true}")
    private boolean screenshotEnabled;

    /** Sample every N completed frames (on `end`) */
    @Value("${rdp.screenshot.sample.every:5}")
    private int sampleEvery;
    
    /** Capture full frame every N samples (to avoid delta frames) */
    @Value("${rdp.screenshot.fullframe.every:3}")
    private int fullFrameEvery;
    
    /** Force full frame capture every N seconds (time-based guarantee) */
    @Value("${rdp.screenshot.fullframe.interval.seconds:5}")
    private int fullFrameIntervalSeconds;

    /** Hard guardrails */
    @Value("${rdp.screenshot.limits.maxStreamsPerSession:8}")
    private int maxStreamsPerSession;

    @Value("${rdp.screenshot.limits.maxBytesPerStream:5242880}") // 5 MiB
    private long maxBytesPerStream;

    /** Consider a stream stale if no blob activity for this many seconds */
    @Value("${rdp.screenshot.limits.staleSeconds:20}")
    private int staleSeconds;

    /** How often to run inline cleanup checks (every N handled instructions) */
    @Value("${rdp.screenshot.cleanup.period.instructions:50}")
    private int cleanupEveryInstructions;

    /* ---------- Per-session single-thread executors (order guaranteed) ---------- */

    private final ConcurrentMap<String, ExecutorService> sessionExecutors = new ConcurrentHashMap<>();

    /* ---------- Session state ---------- */

    private final ConcurrentMap<String, SessionState> sessions = new ConcurrentHashMap<>();

    @Data
    @Builder
    private static class StreamState {
        String streamId;           // e.g. "1.3"
        String mime;               // e.g. "image/png"
        StringBuilder b64;         // base64 buffer
        long byteCount;            // decoded bytes expected so far (approx from base64 len * 3/4)
        Instant createdAt;
        Instant lastUpdateAt;
    }

    @Data
    @Builder
    private static class SessionState {
        String sessionId;
        Map<String, StreamState> streams;   // key: streamId ("layer.index")
        long handledInstructions;
        long completedFrames;               // number of finalized frames
        long savedScreenshots;              // number of screenshots actually saved
        Instant lastActivityAt;
        Instant lastScreenshotAt;           // timestamp of last saved screenshot
        ConnectedSystem connectedSystem;
    }

    /* ---------- Public control ---------- */

    public void startCapture(String sessionId, ConnectedSystem connectedSystem) {
        if (!screenshotEnabled) {
            log.debug("Screenshot capture disabled; not starting for session {}", sessionId);
            return;
        }
        sessions.compute(sessionId, (k, existing) -> {
            if (existing != null) return existing; // idempotent
            var state = SessionState.builder()
                .sessionId(sessionId)
                .streams(new ConcurrentHashMap<>())
                .handledInstructions(0)
                .completedFrames(0)
                .savedScreenshots(0)
                .lastActivityAt(Instant.now())
                .lastScreenshotAt(null)  // No screenshot captured yet
                .connectedSystem(connectedSystem)
                .build();
            log.info("Screenshot capture ENABLED for session {} (sampleEvery={} frames, fullFrameEvery={} screenshots, fullFrameInterval={}s)", 
                sessionId, sampleEvery, fullFrameEvery, fullFrameIntervalSeconds);
            return state;
        });
        sessionExecutors.computeIfAbsent(sessionId, k -> Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "rdp-shot-" + sessionId);
            t.setDaemon(true);
            return t;
        }));
    }

    public void stopCapture(String sessionId) {
        var state = sessions.remove(sessionId);
        if (state != null) {
            state.streams.clear();
            log.info("Stopped screenshot capture for session {}", sessionId);
        }
        var exec = sessionExecutors.remove(sessionId);
        if (exec != null) exec.shutdownNow();
    }

    /* ---------- Entry point from your tunnel reader ---------- */

    public void processInstruction(String sessionId, String rawInstruction) {
        if (!screenshotEnabled) return;
        final var exec = sessionExecutors.get(sessionId);
        if (exec == null) return; // not tracking this session (or already stopped)

        final var instruction = sanitize(rawInstruction);
        exec.submit(() -> {
            try {
                handleInstruction(sessionId, instruction);
            } catch (Throwable t) {
                log.warn("Error handling instruction for session {}: {}", sessionId, t.getMessage());
            }
        });
    }

    /* ---------- Core handling (single-threaded per session) ---------- */

    private void handleInstruction(String sessionId, String instruction) {
        final var state = sessions.get(sessionId);
        if (state == null) return; // already stopped
        state.lastActivityAt = Instant.now();
        state.handledInstructions++;

        // Opportunistic cleanup every N instructions
        if (cleanupEveryInstructions > 0 && state.handledInstructions % cleanupEveryInstructions == 0) {
            cleanupStaleStreams(state);
        }

        // Parse opcode (first token): "3.img,..." → opcode = "img"
        final String opcode = parseOpcode(instruction);
        if (opcode == null) return;

        switch (opcode) {
            case "img" -> onImg(state, instruction);
            case "blob" -> onBlob(state, instruction);
            case "end" -> onEnd(state, instruction);
            default -> {
                // Ignore other instructions – not image data
            }
        }
    }

    /* ---------- Opcode handlers ---------- */

    private void onImg(SessionState state, String instruction) {
        final String streamId = parseSecondToken(instruction);
        if (streamId == null) return;

        if (state.streams.size() >= maxStreamsPerSession) {
            // Drop oldest stream to stay within cap
            dropOldestStream(state, "maxStreamsPerSession");
        }

        final String mime = extractMime(instruction);
        if (!mime.startsWith("image/")) {
            // Not an image stream → ignore
            return;
        }

        final var stream = StreamState.builder()
            .streamId(streamId)
            .mime(mime)
            .b64(new StringBuilder(Math.min((int) (maxBytesPerStream * 4 / 3), 512 * 1024)))
            .byteCount(0)
            .createdAt(Instant.now())
            .lastUpdateAt(Instant.now())
            .build();

        state.streams.put(streamId, stream);
        log.debug("IMG start s={} id={} mime={} (active streams={})",
            state.sessionId, streamId, mime, state.streams.size());
    }

    private void onBlob(SessionState state, String instruction) {
        final String streamId = parseSecondToken(instruction);
        if (streamId == null) return;

        final var stream = state.streams.get(streamId);
        if (stream == null) {
            log.debug("Blob for unknown stream {} in session {}. Ignoring.", streamId, state.sessionId);
            return;
        }

        final String chunk = extractBlobChunk(instruction);
        if (chunk.isEmpty()) return;

        // Predict decoded size growth to enforce cap early
        long predictedDecodedBytes = stream.byteCount + (chunk.length() * 3L) / 4L;
        if (predictedDecodedBytes > maxBytesPerStream) {
            log.warn("Stream {} in session {} exceeded maxBytesPerStream ({} > {}). Dropping stream.",
                streamId, state.sessionId, predictedDecodedBytes, maxBytesPerStream);
            state.streams.remove(streamId);
            return;
        }

        stream.b64.append(chunk);
        stream.byteCount = predictedDecodedBytes;
        stream.lastUpdateAt = Instant.now();
    }

    private void onEnd(SessionState state, String instruction) {
        final String streamId = parseSecondToken(instruction);
        if (streamId == null) return;

        final var stream = state.streams.remove(streamId);
        if (stream == null) {
            log.debug("End for unknown stream {} in session {}. Ignoring.", streamId, state.sessionId);
            return;
        }

        // Completed a frame
        state.completedFrames++;

        // Sampling on FULL frame only
        if (sampleEvery > 1 && (state.completedFrames % sampleEvery != 0)) {
            log.debug("Frame {} for session {} skipped (sampleEvery={})",
                state.completedFrames, state.sessionId, sampleEvery);
            return;
        }

        // Decode and persist
        if (stream.b64.length() == 0) {
            log.debug("Stream {} in session {} ended with empty data.", streamId, state.sessionId);
            return;
        }

        try {
            byte[] image = Base64.getDecoder().decode(stream.b64.toString());
            
            // Apply additional filtering to capture better quality screenshots
            boolean shouldCapture = shouldCaptureScreenshot(state, image);
            
            if (!shouldCapture) {
                log.debug("Screenshot skipped for session {} - not a full frame candidate (savedCount: {})", 
                    state.sessionId, state.savedScreenshots);
                return;
            }
            
            if (image.length < 1024) {
                log.trace("Tiny image ({} bytes) discarded for session {} stream {}", image.length, state.sessionId, streamId);
                return;
            }

            var entity = RdpSessionScreenshot.builder()
                .sessionId(state.sessionId)
                .capturedAt(Instant.now())
                .imageData(image)
                .imageFormat(mimeToFormat(stream.mime))
                .fileSize((long) image.length)
                .processed(false)
                .build();

            screenshotRepository.save(entity);
            state.savedScreenshots++;
            state.lastScreenshotAt = Instant.now();  // Track when we last saved a screenshot
            log.info("Saved screenshot: session={} stream={} bytes={} frames#={} saved#={}",
                state.sessionId, streamId, image.length, state.completedFrames, state.savedScreenshots);

        } catch (IllegalArgumentException e) {
            log.warn("Base64 decode failed for session {} stream {}: {}", state.sessionId, streamId, e.getMessage());
        } catch (Exception e) {
            log.error("Persist failed for session {} stream {}", state.sessionId, streamId, e);
        }
    }
    
    /**
     * Determines if a screenshot should be captured based on multiple criteria.
     * Uses time-based, count-based, and size-based heuristics to ensure we get full frames.
     */
    private boolean shouldCaptureScreenshot(SessionState state, byte[] image) {
        Instant now = Instant.now();
        
        // PRIORITY 1: Time-based guarantee - force capture if enough time has passed
        // This is the most reliable way to ensure periodic full frames
        if (fullFrameIntervalSeconds > 0) {
            if (state.lastScreenshotAt == null) {
                // First screenshot - always capture
                log.debug("Capturing first screenshot for session {}", state.sessionId);
                return true;
            }
            
            long secondsSinceLastCapture = now.getEpochSecond() - state.lastScreenshotAt.getEpochSecond();
            if (secondsSinceLastCapture >= fullFrameIntervalSeconds) {
                log.info("Forcing full frame capture for session {} ({}s since last, threshold: {}s)", 
                    state.sessionId, secondsSinceLastCapture, fullFrameIntervalSeconds);
                return true;
            }
        }
        
        // PRIORITY 2: Count-based guarantee - capture every Nth screenshot
        if (fullFrameEvery > 0 && (state.savedScreenshots % fullFrameEvery == 0)) {
            log.debug("Capturing periodic full frame for session {} (every {} screenshots)", 
                state.sessionId, fullFrameEvery);
            return true;
        }
        
        // PRIORITY 3: Size-based heuristic - larger images are more likely to be full frames
        // Note: This is less reliable as both full and partial screens can be similar sizes
        long imageSizeKB = image.length / 1024;
        boolean isLikelyFullFrame = imageSizeKB > 100;  // Increased threshold to 100KB
        
        if (isLikelyFullFrame) {
            log.debug("Capturing likely full frame for session {} ({} KB)", state.sessionId, imageSizeKB);
            return true;
        }
        
        // Skip images that don't meet any criteria
        log.trace("Skipping screenshot for session {} ({} KB, {}s since last)", 
            state.sessionId, imageSizeKB, 
            state.lastScreenshotAt != null ? (now.getEpochSecond() - state.lastScreenshotAt.getEpochSecond()) : 0);
        return false;
    }

    /* ---------- Helpers ---------- */

    private void cleanupStaleStreams(SessionState state) {
        final Instant cutoff = Instant.now().minusSeconds(staleSeconds);
        state.streams.values().removeIf(s -> s.lastUpdateAt.isBefore(cutoff));
    }

    private void dropOldestStream(SessionState state, String reason) {
        StreamState oldest = null;
        for (var s : state.streams.values()) {
            if (oldest == null || s.createdAt.isBefore(oldest.createdAt)) {
                oldest = s;
            }
        }
        if (oldest != null) {
            state.streams.remove(oldest.streamId);
            log.warn("Dropped oldest stream {} in session {} (reason: {})", oldest.streamId, state.sessionId, reason);
        }
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        // Trim + remove any stray NULs
        return s.replace("\u0000", "").trim();
    }

    /** Returns opcode string (e.g., "img", "blob", "end") or null */
    private static String parseOpcode(String instruction) {
        // Instruction format is "LEN.name,...;" but your reader already hands us decoded text like "3.img,..."
        // So find the first '.' then read until first ',' or ';'
        int dot = instruction.indexOf('.');
        if (dot < 0 || dot + 1 >= instruction.length()) return null;
        int comma = instruction.indexOf(',', dot + 1);
        int semi  = instruction.indexOf(';', dot + 1);
        int end = (comma >= 0 && semi >= 0) ? Math.min(comma, semi)
            : (comma >= 0 ? comma : semi);
        if (end < 0) end = instruction.length();
        return instruction.substring(dot + 1, end);
    }

    /** Returns the second token (after opcode), typically stream id like "1.3" */
    private static String parseSecondToken(String instruction) {
        // tokens split by ',' → 0:"3.img" 1:"1.3" 2:"..."
        int firstComma = instruction.indexOf(',');
        if (firstComma < 0) return null;
        int secondComma = instruction.indexOf(',', firstComma + 1);
        int end = secondComma >= 0 ? secondComma : instruction.indexOf(';', firstComma + 1);
        if (end < 0) end = instruction.length();
        return instruction.substring(firstComma + 1, end).trim();
    }

    /** Extract "image/..." MIME if present, defaults to image/png */
    private static String extractMime(String instruction) {
        int idx = instruction.indexOf("image/");
        if (idx < 0) return "image/png";
        int end = instruction.indexOf(',', idx);
        if (end < 0) end = instruction.indexOf(';', idx);
        if (end < 0) end = instruction.length();
        return instruction.substring(idx, end).trim();
    }

    /** Extract base64 payload from "4.blob,<stream>,<len>.<b64>;" */
    private static String extractBlobChunk(String instruction) {
        int firstComma = instruction.indexOf(',');
        if (firstComma < 0) return "";
        int secondComma = instruction.indexOf(',', firstComma + 1);
        if (secondComma < 0) return "";
        String dataPart = instruction.substring(secondComma + 1).trim();
        if (dataPart.endsWith(";")) dataPart = dataPart.substring(0, dataPart.length() - 1);
        int dot = dataPart.indexOf('.');
        if (dot >= 0 && dot + 1 < dataPart.length()) {
            return dataPart.substring(dot + 1);
        }
        return dataPart;
    }

    private static String mimeToFormat(String mime) {
        if (mime == null) return "PNG";
        if (mime.equalsIgnoreCase("image/jpeg") || mime.equalsIgnoreCase("image/jpg")) return "JPEG";
        if (mime.equalsIgnoreCase("image/webp")) return "WEBP";
        if (mime.equalsIgnoreCase("image/png")) return "PNG";
        // fallback
        return "PNG";
    }

    /* ---------- Introspection ---------- */

    public int getActiveCaptureCount() {
        return (int) sessions.values().stream().filter(Objects::nonNull).count();
    }

    public boolean isEnabled() {
        return screenshotEnabled;
    }
}