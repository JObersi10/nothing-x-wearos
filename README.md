# nothing-x-wearos

A Wear OS companion app to control Nothing / CMF earbuds from your watch —
battery, ANC, and EQ, no phone required (in the primary connection path).
Inspired by Samsung's Galaxy Buds Controller app for Wear OS.

Built primarily for a Galaxy Watch 4, targeting Wear OS 3+ generally.

## Status

Early build. The protocol layer is implemented and unit-tested for real. The
watch app and Tile are written but **not yet built or run on real hardware**
— see `HANDOFF.md` for exactly what's verified vs. not, and what to try first.

## Repo layout

```
protocol/    Pure-Kotlin (no Android deps) frame codec, command IDs, CRC16,
             and the EarbudsSession state machine. JVM-testable anywhere.
bluetooth/   Android library: direct RFCOMM transport (watch -> earbuds),
             bonded-device listing, connection state.
wear/        The Wear OS app itself (Jetpack Compose + a quick-glance Tile).
phone/       Scaffold only, NOT functional — landing spot for a future
             phone-relay fallback transport. See its MainActivity doc comment.
```

## Building

You need the Android SDK — this was written in a sandbox that couldn't
resolve `dl.google.com`, so the Android modules have never actually been
built. Get it building on a machine with real internet access first.

```bash
cp local.properties.example local.properties
# edit local.properties to point sdk.dir at your Android SDK

./gradlew :protocol:test          # pure-Kotlin, should just work
./gradlew :bluetooth:assembleDebug
./gradlew :wear:assembleDebug
./gradlew :phone:assembleDebug
```

Install to a watch over ADB (wireless debugging or USB via the watch's
developer options):

```bash
adb install -r wear/build/outputs/apk/debug/wear-debug.apk
```

## Protocol

The Nothing Ear "0x55" RFCOMM protocol is not something this project
reverse-engineered itself — it's ported from two existing open-source
projects that already did that work against the Nothing Android APK:
[something-x](https://github.com/SoaOaoS/something-x) (Linux desktop app) and
[ear-web](https://github.com/radiance-project/ear-web) (Web Bluetooth). See
`protocol/src/main/kotlin/com/nothingx/protocol/Commands.kt` for the command
IDs this app implements and `THIRD_PARTY_NOTICES.md` for attribution.

CMF Buds command IDs are **unverified** — both source projects flag this.
Treat a CMF device connecting as "try it, capture raw frames if it
misbehaves," not "confirmed working."

## License

MIT for this repo's own code (see `LICENSE`). A handful of icon files are
adapted from an AGPLv3 project — see `THIRD_PARTY_NOTICES.md` before
distributing the built APK anywhere beyond your own devices.
