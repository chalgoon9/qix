# Qix Panic (Android)

A minimal Qix-like area capture game implemented with Kotlin + SurfaceView. Drag on the screen or use the on-screen D-Pad to change direction. Leave the border to start drawing; close a loop to claim area. Avoid enemies hitting your trail. Claim 75% to clear.

## Build
- Open `qixpanic` in Android Studio (Giraffe or newer).
- Let it sync Gradle. Run the `app` configuration on an emulator or device.

## Controls
- Touch + drag: change direction (4-way).
- On-screen D-Pad: tap arrows to change direction.
- Top-right button: Pause/Resume.
- Tap after Level Clear / Game Over to continue/restart.

## Notes
- Grid: 96x64 logical cells.
- Enemies increase with level; speed ramps slightly.
- Trail collision costs a life; score = claimed cells.
