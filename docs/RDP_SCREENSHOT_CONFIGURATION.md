# RDP Screenshot Capture Configuration

## Overview

The RDP proxy includes intelligent screenshot capture with **time-based full frame forcing** to ensure reliable periodic full screenshots, regardless of frame size variations.

## Configuration Properties

Add these properties to your `application.properties` file:

```properties
# Enable/disable screenshot capture (default: true)
rdp.screenshot.enabled=true

# Sample every N completed frames (default: 5)
# Higher values = fewer screenshots captured overall
rdp.screenshot.sample.every=5

# Capture full frame every N samples (default: 3)
# Backup mechanism for periodic captures
rdp.screenshot.fullframe.every=3

# **RECOMMENDED**: Force full frame capture every N seconds (default: 30)
# This is the most reliable way to ensure periodic full frames
# Set to 0 to disable time-based forcing
rdp.screenshot.fullframe.interval.seconds=30

# Maximum streams per session (default: 8)
rdp.screenshot.limits.maxStreamsPerSession=8

# Maximum bytes per stream (default: 5242880 = 5MB)
rdp.screenshot.limits.maxBytesPerStream=5242880

# Stream stale timeout in seconds (default: 20)
rdp.screenshot.limits.staleSeconds=20

# Cleanup check frequency (default: 50 instructions)
rdp.screenshot.cleanup.period.instructions=50
```

## How Full Frame Detection Works

The capture service uses a **three-tier priority system** to ensure reliable full frame captures:

### Priority 1: Time-Based Forcing (MOST RELIABLE) ⭐
- **Interval**: Configurable via `fullframe.interval.seconds` (default: 30s)
- **How it works**: Forces a screenshot capture after N seconds since the last saved screenshot
- **Why it works**: Independent of frame size or content, guarantees periodic captures
- **Example**: With `fullframe.interval.seconds=30`, you get at least one screenshot every 30 seconds

This is the **recommended approach** because:
- ✅ Reliable regardless of RDP protocol variations
- ✅ Predictable capture frequency
- ✅ Works even when size heuristics fail (e.g., 135KB full + 85KB partial)
- ✅ Easy to configure and understand

### Priority 2: Count-Based Guarantee
- **Interval**: Every Nth saved screenshot (`fullframe.every`)
- **How it works**: Captures every Nth screenshot that passes the sampling filter
- **Use case**: Backup mechanism when time-based forcing isn't suitable

### Priority 3: Size-Based Heuristic (LEAST RELIABLE)
- **Threshold**: Images larger than 100KB (increased from 50KB)
- **How it works**: Assumes larger images are more likely full frames
- **Limitation**: Not reliable as both full and partial screens can have similar sizes
- **Use case**: Opportunistic captures between time intervals

## Analytics Configuration

The RDP Session Summarization Agent now:
- Selects up to 4 screenshots (reduced from 6) for LLM analysis
- Prioritizes larger screenshots (full frames) over smaller ones (deltas)
- Analyzes in batches of 2 with context building
- Sorts screenshots by size before selection to get the best quality images

## Example Configuration for Different Use Cases

### Recommended: Time-Based with 30-Second Intervals (Default)
```properties
rdp.screenshot.sample.every=5
rdp.screenshot.fullframe.interval.seconds=30
rdp.screenshot.fullframe.every=3
```
This captures a screenshot every 30 seconds (time-based), with backup count-based capture every 3rd screenshot.

### High-Frequency Capture (Every 15 Seconds)
```properties
rdp.screenshot.sample.every=3
rdp.screenshot.fullframe.interval.seconds=15
rdp.screenshot.fullframe.every=2
```
More frequent captures for detailed session monitoring.

### Low-Frequency Capture (Every Minute)
```properties
rdp.screenshot.sample.every=10
rdp.screenshot.fullframe.interval.seconds=60
rdp.screenshot.fullframe.every=5
```
Fewer screenshots, lower storage requirements.

### Size-Only Mode (Not Recommended)
```properties
rdp.screenshot.sample.every=5
rdp.screenshot.fullframe.interval.seconds=0  # Disable time-based
rdp.screenshot.fullframe.every=0              # Disable count-based
```
Only captures based on size (>100KB). Not recommended due to unreliability.

## Monitoring

Check logs for screenshot capture activity:
- `INFO` level: Shows when screenshots are saved and forced captures
- `DEBUG` level: Shows capture decision logic
- `TRACE` level: Shows skipped screenshots

Example log output:
```
INFO  - Forcing full frame capture for session rdp-123 (35s since last, threshold: 30s)
INFO  - Saved screenshot: session=rdp-123 stream=1.3 bytes=125480 frames#=25 saved#=5
DEBUG - Capturing first screenshot for session rdp-123
DEBUG - Capturing likely full frame for session rdp-123 (122 KB)
TRACE - Skipping screenshot for session rdp-123 (45 KB, 12s since last)
```

## Understanding Time-Based Forcing

### How It Works

1. **First Screenshot**: Always captured (no previous screenshot exists)
2. **Subsequent Frames**: 
   - Check time since last capture
   - If ≥ `fullframe.interval.seconds`, force capture
   - Otherwise, evaluate count-based and size-based criteria

### Why Time-Based Is Better

The issue with size-based detection:
- **Problem**: A 135KB image might be a full screen, but an 85KB image could be a partial screen
- **Reality**: RDP compression and content variations make size unreliable
- **Solution**: Time-based forcing guarantees captures regardless of size

Example scenario:
```
Time  | Size   | Size-Based  | Time-Based (30s)
------|--------|-------------|------------------
0:00  | 135KB  | ✓ Captured  | ✓ Captured (first)
0:15  | 85KB   | ✓ Captured  | ✗ Skipped (15s < 30s)
0:32  | 45KB   | ✗ Skipped   | ✓ Captured (32s ≥ 30s)
1:05  | 65KB   | ✓ Captured  | ✓ Captured (33s ≥ 30s)
```

With time-based forcing, you get predictable capture intervals regardless of RDP protocol behavior.

## Impact on LLM Analysis

With these changes:
- **Better Quality**: LLM receives full context screenshots instead of partial deltas
- **Reduced Cost**: Fewer screenshots means fewer API tokens used
- **Better Analysis**: Full frames provide complete context for activity detection
- **Consistent Results**: More reliable detection of applications and user actions

## Troubleshooting

### Too Many Screenshots Being Captured
- Increase `rdp.screenshot.sample.every` to capture less frequently
- Increase the 50KB threshold in `shouldCaptureScreenshot()` method

### Not Enough Screenshots
- Decrease `rdp.screenshot.sample.every`
- Decrease `rdp.screenshot.fullframe.every` to force more captures

### Screenshots Too Small
- Check if RDP client is sending low-quality images
- Verify network bandwidth is sufficient for full frame transmission
- Consider RDP color depth and compression settings

## Technical Details

### Full Frame vs Delta Detection

The system determines if a screenshot is likely a full frame using:

1. **Image Size**: Full frames are typically 50KB+ for standard resolutions
2. **Periodic Capture**: Forces capture every N screenshots regardless of size
3. **Chronological Distribution**: Selects screenshots evenly across session timeline

### Screenshot Selection Algorithm

When the analytics agent processes a session:

1. Sorts all screenshots by file size (descending)
2. Takes top 2x candidates (e.g., top 8 for 4 needed)
3. Sorts candidates by capture time (chronological)
4. Selects evenly distributed from candidates
5. Processes in batches of 2 with context

This ensures the LLM receives the highest quality, most representative screenshots.
