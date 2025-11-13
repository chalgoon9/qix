# Qix Panic (Android)

A minimal Qix-like area capture game implemented with Kotlin + SurfaceView. Drag on the screen to set direction. Leave the border to start drawing; close a loop to claim area. Avoid enemies hitting your trail. Claim 75% to clear.

## Build

- Open `qixpanic` in Android Studio (Giraffe or newer).
- Let it sync Gradle. Run the `app` configuration on an emulator or device.

## Controls

- Touch + drag: change direction (4-way).
- Moving from border into empty starts drawing.

## Notes

- Grid: 96x64 logical cells.
- Two bouncing enemies; trail collision costs a life.
- Simple flood-fill to claim the region not connected to enemies.

This is a foundation you can extend with levels, power-ups, art, and sound.
