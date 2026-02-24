# Background Removal Test Checklist

1. Online success path
- Open Create screen.
- Pick image.
- Tap Remove BG, accept disclosure once.
- Keep mode on High quality (online), wait for preview.
- Toggle Before/After and apply.
- Verify transparent/selected background output.

2. Offline fallback path
- Disable internet.
- Pick image and run Remove BG with Offline (basic).
- Verify output is generated and can be applied.

3. Cancellation
- Start background removal.
- Tap Cancel while processing.
- Verify no crash and normal UI recovery.

4. Large image handling
- Use a high-resolution image.
- Start Remove BG.
- Verify app remains responsive and no OOM/crash.

5. API failure handling
- Force backend to return 500/timeout.
- Verify friendly error message and Retry works.

6. Save PNG
- Generate preview and tap Save PNG.
- Verify saved image exists in Pictures/PicPose.
