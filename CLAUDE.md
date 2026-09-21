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

### Field-confirmed on real hardware (2026-09-21)

Direct RFCOMM connect from a Galaxy Watch 4 to a CMF Buds Pro 2 works, and
ANC mode switching round-trips correctly over it. This resolves the central
open question below — the reflection-based channel connect **is** viable on
Wear OS 3, at least on this hardware pairing. Battery and EQ round-trip on
CMF specifically are still unconfirmed (user hasn't reported on those yet).
Bonded-device matching had a real UX bug found in this same session: the
device list showed every paired Bluetooth device with only a color hint for
which one was Nothing/CMF gear, which wasn't legible enough — fixed by
filtering to matched devices by default with a "show all" fallback chip
(`DeviceListScreen.kt`).

### The reflection-based RFCOMM channel connect (`DirectRfcommTransport`)

Android's public `BluetoothDevice` API only exposes
`createRfcommSocketToServiceRecord(UUID)` — an SDP lookup by service UUID.
The Nothing protocol has no known SDP UUID; something-x and ear-web both
connect by raw RFCOMM channel number instead (which Linux BlueZ and Web
Serial both allow directly). On Android, that requires the same private
`createRfcommSocket(int channel)` reflection call that every Bluetooth-SPP-
terminal app on the Play Store relies on for the same reason
(`openRfcommChannel` in `DirectRfcommTransport.kt`). This has worked across
Android versions for years but isn't a stable public contract. **Confirmed
working** on a Galaxy Watch 4 connecting directly to a CMF Buds Pro 2 (see
the field-confirmed note above) — this was the single biggest unverified
risk in the whole direct-connect design, and it panned out. Still worth
treating carefully on other hardware/Android version combinations since
it's a private API, not a stable public contract.

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

### v1 core scope vs. the settings screen (mined 2026-09-21)

Battery, ANC (off/on/transparency), EQ presets were v1's core scope, matching
something-x's confirmed-working set. The user then asked for the broader
settings list ear-web supports, so its command IDs got mined into
`Commands.kt` for real — in-ear detection, low latency mode, personalized
ANC, bass enhance/"Ultra Bass", find-my-earbuds (ring), ear tip fit test, and
a read-only gesture count. Source: `ear-web/res/js/bluetooth_socket.js`'s
`send(command, ...)` calls — every command ID there is a literal decimal
constant in code that project's users run against real hardware, so these
are as trustworthy as the core set, not guesses (see `Commands.kt`'s doc
comment for detail). **Not implemented**: gesture *editing* (read-only count
only — the per-gesture array structure is more UI work than this pass
covers), custom EQ (complex float-encoded 53-byte payload, `SET_CUSTOM_EQ`
constant exists but no builder), CMF's separate "Listening Mode" EQ-equivalent
command (`GET_LISTENING_MODE`/`SET_LISTENING_MODE` — CMF Buds/Buds Pro/Buds
Pro 2 use a *different* command than `GET_EQ_MODE`/`SET_EQ` for what's
functionally the same feature; this app's EQ UI is currently hidden anyway,
see below), Spatial Audio (confirmed to exist in the official Nothing X app
via a user-provided screenshot, but neither something-x nor ear-web
reverse-engineered it — no command ID available without decompiling the
official APK directly, which hasn't been done here), Bixby (Samsung-only,
no Nothing/CMF equivalent exists at all).

EQ UI stays hidden per earlier user direction (not a priority). Worth noting
for whenever it comes back: the current `SET_EQ`/`GET_EQ_MODE` commands only
work on Nothing Ear models — CMF devices need `SET_LISTENING_MODE`/
`GET_LISTENING_MODE` instead, so re-enabling EQ for a CMF device (like the
CMF Buds Pro 2 this was tested against) needs that branch, not just un-hiding
the existing chips.

### Compose compiler version, not a Gradle plugin

`wear`'s Compose UI needs a Compose *compiler* version matched to the Kotlin
version. On Kotlin 2.0+ that's the `org.jetbrains.kotlin.plugin.compose`
Gradle plugin. This project is pinned to Kotlin 1.9.24 (see the toolchain
note above), where that plugin doesn't exist at all — CI's first real build
of `wear` failed on exactly this ("Plugin ... was not found"). On 1.9.24 the
compiler version is set via `composeOptions { kotlinCompilerExtensionVersion
= "1.5.14" }` in `wear/build.gradle.kts`'s `android {}` block instead. If
Kotlin ever gets bumped to 2.0+, switch to the plugin and drop
`composeOptions` — don't run both.

### Connection lifecycle lives at the device-session level, not per-screen

Real bug, confirmed from the user's own logcat: an earlier version tied the
RFCOMM connection's lifetime to `DeviceDetailScreen`'s own Compose
lifecycle (`DisposableEffect { connect(); onDispose { disconnect() } }`).
Navigating to Settings — a separate nav destination — disposed that
composable and fired `onDispose`, tearing down the connection while the
user was still in the middle of a device session. Every settings command
after that silently logged "no output stream (not connected), dropped" —
Find My Earbuds, Ear Tip Fit Test, and Low Lag Mode all looked individually
broken but the actual cause was one line.

Fixed by moving disconnect to the one place that's the real "left this
device" signal: `DeviceListScreen`'s `LaunchedEffect(Unit)`, which only
re-runs when the user is actually back at the list. `DeviceDetailScreen`
now just ensures a connection exists (`LaunchedEffect`, no dispose-time
teardown), and `DeviceViewModel.connect()` guards against restarting an
already-live connection to the same address so re-entering the detail
screen from Settings doesn't churn the socket. **Don't reintroduce a
disconnect tied to a specific screen's composition** — any future screen
added to the device-session nav graph (detail, settings, a future gestures
editor, etc.) needs to NOT disconnect on its own dispose, only
`DeviceListScreen` should.

### Battery display is a letter badge, not an icon

First attempt used hand-drawn `ic_bud.xml`/`ic_case.xml` vector pictograms
that were never visually verified before shipping (this sandbox has no way
to render a vector drawable) — they looked wrong on real hardware, per user
feedback. Replaced with a circle + bold letter (L/C/R), which needs no
custom art to render correctly. If earbud/case iconography comes back,
actually check it renders acceptably (a screenshot from the user, or a
local Compose preview) before shipping it, not just "the path data looks
plausible."

### Tile now controls ANC without opening the app (2026-09-21)

This replaced the earlier "Tile is read-only" design. The connection is now
process-wide and persistent, not owned by any one screen or ViewModel:

- **`EarbudsConnectionHolder`** (`wear/.../connection/`) — a singleton
  object holding the one live `EarbudsTransport`. Both `MainActivity` (via
  `DeviceViewModel`, now a thin wrapper) and `NothingXTileService` read/act
  on this same instance, since a Tile provider and the app's Activity are
  the same process on Wear OS (no separate `android:process` declared) —
  no cross-process IPC needed, just a plain singleton.
- **`EarbudsConnectionService`** — a foreground service (`connectedDevice`
  type) whose only job is keeping the process alive in the background so
  the connection survives after the user leaves the app. Started from
  `DeviceViewModel.connect()`, stopped only by the explicit "Disconnect"
  chip in `SettingsScreen` — **not** by navigating within the app anymore
  (see the trap below).
- **`TileActionActivity`** — an invisible trampoline activity
  (`Theme.Transparent`, `excludeFromRecents`, `noHistory`) that the Tile's
  ANC dots launch with a target mode extra; it calls
  `EarbudsConnectionHolder.setAncMode()` and finishes before any frame
  draws, so nothing visible happens. Deliberately used instead of
  ProtoLayout's native `LoadAction`+state round-trip (the "correct" way to
  build an interactive Tile) — that pattern needs several ProtoLayout APIs
  this project had never exercised, in a sandbox that can't compile-check
  any of it. The trampoline reuses only `ActionBuilders.LaunchAction`,
  which the Tile already used successfully to open `MainActivity`. Lower
  risk; the user can't tell the difference in practice.

**Trap: don't reintroduce a disconnect tied to navigation.** An earlier fix
this same session had `DeviceListScreen` call `disconnect()` every time the
user returned to it, to fix a different bug (connection dying when
navigating to Settings). That directly conflicts with the persistent Tile
connection — it would kill the Tile's connection on every trip back to the
list. Removed; disconnecting is now only ever explicit (Settings' Disconnect
chip) or automatic process death. If a "leave this device" signal is needed
again, do **not** put it on `DeviceListScreen`'s composition lifecycle.

**Unverified**: none of this has touched a real device yet (built after the
last field-test round). Specific risks: `ActionBuilders.AndroidActivity
.addKeyToExtraMapping()` with `AndroidStringExtra` for passing the target
ANC mode to the trampoline activity is a ProtoLayout API surface this
project hasn't exercised before; the foreground service's
`connectedDevice` type and `POST_NOTIFICATIONS` runtime permission
(requested via `PermissionGate`) need on-device confirmation that the
notification actually shows and the service actually survives backgrounding
long enough to matter; and there's no reconnect-on-boot or retry logic if
the earbuds go out of range while the service is running in the background
— it'll sit disconnected until the app is reopened or the Tile is tapped
again.

### `phone/` module

Scaffolded to prove the `:protocol`/`:bluetooth` dependency wiring works from
a second Android app, nothing more. No Data Layer listener, no relay logic.
Don't let a stale doc or commit message imply otherwise — check
`phone/src/main/kotlin/com/nothingx/phone/MainActivity.kt`'s doc comment,
which is the source of truth on what's there.

## Icons and branding

`wear/src/main/res/drawable/ic_anc_*.xml`, `ic_arrow_right.xml`, `ic_back.xml`
are AGPLv3 (ported from ear-web). `ic_earbuds.xml` is original. See
`THIRD_PARTY_NOTICES.md` before touching licensing-sensitive files.

**Never use Nothing's actual trademarked assets** (their app icon, wordmark,
NType/Ndot fonts) in this repo, even if a screenshot of them shows up in
conversation. This app is unofficial and unaffiliated with Nothing
Technology — using their real logo as this app's launcher icon would
visually claim official status/endorsement, a different and more serious
problem than the AGPL code-reuse question above (see ear-web's own README
disclaimer for why every project like this one carries one). The launcher
icon (`ic_launcher_background.xml`/`ic_launcher_foreground.xml`, adaptive
icon via `mipmap-anydpi-v26/ic_launcher.xml`) is an original mark — a white
ring with a Nothing-red accent dot, echoing the app's own ANC-selector
selected-state dot, not a reproduction of anything official. When "leverage
the Nothing design language" comes up again: colors, rounded-card layout,
and general minimalist aesthetic are fair game; their specific logo, fonts,
and any asset that could be mistaken for the real app are not.
