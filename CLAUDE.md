# CLAUDE.md

Working memory for any AI (or human) picking this repo up. Keep this current
as part of the work, not after.

## What this is

Wear OS app to control Nothing/CMF earbuds directly from the watch (battery,
ANC, EQ) — no phone required in the primary path. Built for a Galaxy Watch 4
first, Wear OS 3+ generally. See `README.md` for user-facing setup,
`HANDOFF.md` for current state / what's verified.

## Key paths

- `protocol/` — pure Kotlin, no Android deps. Frame codec, command IDs, CRC16,
  `EarbudsSession` state machine. This is the only module unit-tested so far
  (17 tests, all green, see `protocol/src/test/`).
- `bluetooth/` — Android library. `DirectRfcommTransport` is the watch's own
  RFCOMM connection to the earbuds. `BondedDevices` lists paired Classic BT
  devices. `NothingDeviceMatcher` name-matches supported devices.
- `wear/` — the actual Wear OS app. Compose UI (`ui/`), `DeviceViewModel`
  wiring the transport to Compose state, a Tile (`tile/NothingXTileService`).
- `phone/` — **scaffold only, not functional.** Landing spot for a future
  phone-relay fallback transport. Do not describe this as working.

## Build commands

```bash
cp local.properties.example local.properties   # point sdk.dir at your Android SDK
./gradlew :protocol:test                       # works anywhere with JDK 17+
./gradlew :bluetooth:assembleDebug
./gradlew :wear:assembleDebug
./gradlew :phone:assembleDebug
adb install -r wear/build/outputs/apk/debug/wear-debug.apk
```

`local.properties` is gitignored (machine-specific SDK path). Copy from
`local.properties.example`.

## Non-obvious decisions and traps

### Why root `build.gradle.kts` has no `plugins {}` block

This repo was first built in a sandbox where `dl.google.com` was network-
blocked by policy — Android Gradle Plugin (AGP) could never resolve there, at
all, regardless of SDK presence. Declaring AGP versions in root
`build.gradle.kts` with `apply false` still forces Gradle to *resolve* the
plugin for every project during configuration, which fails immediately in
that kind of sandbox. Moving each Android module's plugin declaration into
its own `build.gradle.kts` (with an explicit version, no root-level
declaration) means `./gradlew :protocol:test` never touches AGP resolution at
all and can build standalone. Combined with `org.gradle.configureondemand=true`
in `gradle.properties` (so Gradle doesn't eagerly configure every subproject
for a single-module task), this is what makes the pure-Kotlin module testable
even where Android tooling categorically cannot work. **Don't undo this**
by moving the plugin versions back to root — it silently breaks
`:protocol:test` in any AGP-resolution-restricted environment, CI included if
that ever changes.

### Why `protocol`'s Kotlin JVM toolchain is 21, not 17

The Android modules target JDK 17 (AGP 8.5 requirement zone). `protocol` was
pinned to 17 first and failed in the sandbox described above because only
JDK 21 was installed there. Since `protocol` has zero Android dependencies,
there's no reason it needs to match the Android modules' JDK — it's pinned to
21 for portability. If this ever causes a real conflict (e.g. `bluetooth`
depending on `protocol` and needing matching JDK bytecode targets), lower
`protocol`'s target, not the other way around — `bluetooth`/`wear`/`phone`
being on 17 is the real constraint (AGP-driven), `protocol` being on 21 is
just "whatever was available when it was first verified."

### The reflection-based RFCOMM channel connect (`DirectRfcommTransport`)

Android's public `BluetoothDevice` API only exposes
`createRfcommSocketToServiceRecord(UUID)` — an SDP lookup by service UUID.
The Nothing protocol has no known SDP UUID; something-x and ear-web both
connect by raw RFCOMM channel number instead (which Linux BlueZ and Web
Serial both allow directly). On Android, that requires the same private
`createRfcommSocket(int channel)` reflection call that every Bluetooth-SPP-
terminal app on the Play Store relies on for the same reason
(`openRfcommChannel` in `DirectRfcommTransport.kt`). This has worked across
Android versions for years but isn't a stable public contract. **This is the
single biggest unverified risk in the whole direct-connect design** — nobody
has confirmed a Wear OS watch (as opposed to a phone) can hold this kind of
socket to a third Classic BT device that isn't its own paired phone. First
real hardware test should be exactly this, before spending time on anything
else.

### CMF command IDs are guesses, not confirmed

`Commands.kt`'s IDs come from something-x, confirmed against real Nothing Ear
(2) hardware. CMF Buds are assumed to speak the same command IDs based on
ear-web's "likely same, different cmd IDs possible" note — this is
**unverified**. `NothingDeviceMatcher.isUnverifiedCmf()` flags this in the UI
(device list shows "(unverified)"). If a CMF device connects but behaves
wrong, turn on raw frame logging (not yet wired up — see below) rather than
guessing at new command IDs.

### Raw frame debug logging — not implemented yet

something-x has `SOMETHING_X_DEBUG=1` to dump raw RFCOMM bytes; this repo
doesn't have an equivalent yet. Given the CMF-protocol uncertainty above,
this should be one of the first things added — a debug flag that logs every
`FrameParser`-decoded frame (or better, the raw bytes before parsing) so a
CMF hardware capture can confirm or correct command IDs.

### v1 scope is intentionally narrow

Battery, ANC (off/on/transparency), EQ presets only — matches something-x's
confirmed-working set. ear-web documents a much larger command surface
(gestures, advanced/custom EQ, personalized ANC, find-my-earbuds, in-ear
detection toggle, low-latency mode, bass enhance) that was deliberately left
unmined into `Commands.kt` for v1. When picking that up, `ear-web/res/js/
bluetooth_socket.js` and `control.js` are the source to mine — see
`THIRD_PARTY_NOTICES.md`.

### Tile is read-only, not live

`NothingXTileService` reads cached state from `DevicePrefs` (written by
`DeviceViewModel` every time `deviceState` changes while the app is open) —
it does not hold its own RFCOMM connection. Tapping it opens the app; there's
no in-tile quick-toggle yet. That needs either a bound background service
holding the connection, or the (currently unimplemented) phone relay.

### `phone/` module

Scaffolded to prove the `:protocol`/`:bluetooth` dependency wiring works from
a second Android app, nothing more. No Data Layer listener, no relay logic.
Don't let a stale doc or commit message imply otherwise — check
`phone/src/main/kotlin/com/nothingx/phone/MainActivity.kt`'s doc comment,
which is the source of truth on what's there.

## Icons

`wear/src/main/res/drawable/ic_anc_*.xml`, `ic_arrow_right.xml`, `ic_back.xml`
are AGPLv3 (ported from ear-web). `ic_earbuds.xml` is original. See
`THIRD_PARTY_NOTICES.md` before touching licensing-sensitive files.
