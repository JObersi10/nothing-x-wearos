# HANDOFF

Last updated: 2026-09-21, compileSdk 37 landed + phone relay implemented.
Pending: CI confirmation on commit `08fdc13` (in progress as of this
update — check https://github.com/JObersi10/nothing-x-wearos/actions or
PR https://github.com/JObersi10/nothing-x-wearos/pull/1 for current status
before trusting anything below as "green").

## Where things stand right now

Two big pieces landed this session, on top of an already-confirmed-working
direct-connect + persistent-Tile-connection base:

### 1. Toolchain migration to AGP 9.1.1 / Kotlin 2.2.10 / compileSdk 37 — done

Went through 6 pushes to get fully green (full blow-by-blow in
CLAUDE.md's "Toolchain: AGP 9.1.1 / Kotlin 2.2.10 / compileSdk 37"
section — worth reading if a future build breaks in this area again):
1. `e0cb6f8` — the bump itself. Failed: AGP 9's built-in Kotlin collided
   with the project's leftover explicit `org.jetbrains.kotlin.android`
   plugin application.
2. `4304d63` — dropped the explicit kotlin-android plugin. Failed
   differently: `platforms;android-37` (bare) doesn't exist as a package.
3. `3558657` — stepped compileSdk back to 36 to unblock. Failed: Compose
   1.12.x itself demands compileSdk >= 37, independent of anything else.
4. `33e693c` — pinned compose-bom/wear-compose to versions below that
   floor. **First CI green** of this round, but capped at compileSdk 36.
5. `92a1e51` — added a diagnostic CI step to list every real SDK package
   sdkmanager's feed actually has, instead of guessing further.
6. `08fdc13` — the diagnostic answered it: the real package id is
   `platforms;android-37.0` (this Android release cycle versions the
   platform baseline itself, not just extension levels — `37.0`/`37.1`/
   `37.2` are separate packages). Fixed the CI step, bumped
   compileSdk/targetSdk back to 37 everywhere, restored compose-bom
   `2026.09.00` / wear-compose `1.6.2`. **Wear Widget implementation
   (Phase 2) is now unblocked** — not started yet, but nothing left
   stopping it.

### 2. Phone relay path — implemented, unverified on hardware

`phone/` is no longer a scaffold. Full architecture in CLAUDE.md's
"`phone/` module — relay path implemented" section. Short version:
- `PhoneRelayService` (phone) holds a real `DirectRfcommTransport`
  connection to the earbuds via the phone's own Bluetooth pairing.
- Commands relay watch→phone via `MessageClient` (fire-and-forget,
  matching `DirectRfcommTransport.sendCommand`'s own no-ack semantics).
- State relays phone→watch via `DataClient` (only fires on an actual
  change — no polling, no wasted radio wake-ups).
- Watch side: `WearRelayTransport`, a second `EarbudsTransport`
  implementation. Picked via a new "Use phone relay" toggle in Settings
  (`TransportModePrefs`) — **takes effect on next app start, not live**
  (see its doc comment for why a live hot-swap wasn't attempted).
- Direct connect stays the default; nothing about the existing working
  path changed.

**Completely unverified** — needs an actual watch+phone pair (paired via
the Wear OS companion app), both APKs installed, relay turned on, and a
manual walk-through (see test checklist below).

## APK downloads

As of the last confirmed-green run (commit `33e693c`, before compileSdk 37
landed): https://github.com/JObersi10/nothing-x-wearos/actions/runs/35562509118

**A newer build should exist by the time this is read** — commit `08fdc13`
(compileSdk 37 + phone relay together) was pushed and CI was in progress
when this doc was last updated. Check
https://github.com/JObersi10/nothing-x-wearos/actions/runs/35563341021
(the phone-relay-only run, already confirmed green, includes the relay
code but still at compileSdk 36) or look for the latest run on
`claude/ecstatic-galileo-evyo4w` for the compileSdk-37 build.

GitHub artifact downloads need you logged into GitHub in the browser (the
API zip URLs need an auth token) — open the run page and download from
there. Artifacts expire ~90 days after the run. Install via
`adb install -r wear-debug.apk` after unzipping (same for `phone-debug.apk`
if testing the relay), or sideload however you normally do.

## What's done

- **`protocol`**: frame codec, CRC16/ARC, command IDs, `EarbudsSession`
  state machine. Unit-tested — 17 tests, all green (`protocol/src/test/`).
- **`bluetooth`**: `DirectRfcommTransport`, `BondedDevices`,
  `NothingDeviceMatcher`, and now `relay/RelayProtocol.kt` (shared wire
  format for the relay path).
- **`wear`**: Compose app with a persistent background connection shared
  between the app and the Tile (`EarbudsConnectionHolder`,
  `EarbudsConnectionService`, `TileActionActivity` — see CLAUDE.md's "Tile
  now controls ANC without opening the app"). Device list filters to
  matched devices. Device detail: battery card (letter badges L/C/R), ANC
  card, Settings chip. Settings: in-ear detection, low latency,
  personalized ANC, Ultra Bass slider, Find My Earbuds, Ear Tip Fit Test,
  Disconnect, and now "Use phone relay". Tile: 3 ANC dots are real tap
  targets that change ANC without opening the app.
- **`phone`**: real relay implementation (`PhoneRelayService` + a minimal
  device-picker `MainActivity`). Unverified on hardware.
- CI (`.github/workflows/ci.yml`), MIT `LICENSE` + `THIRD_PARTY_NOTICES.md`.

## What's verified vs. not

**Confirmed on real hardware (Galaxy Watch 4 + CMF Buds Pro 2, 2026-09-21,
before this session's toolchain/relay work):**
- Direct RFCOMM connect and ANC mode switching (off/ANC/transparency) work.

**NOT yet verified — everything from this session:**
- The persistent-connection + in-tile-control architecture (unverified
  since the round before this one — still true, nothing since has tested
  it on-device).
- Whether the compileSdk 37 / new Compose version bump changed anything
  observable in the running app (should be invisible, but "should" isn't
  "confirmed").
- The entire phone relay path, start to finish (see above).
- Battery and EQ round-trip on CMF specifically (user has only confirmed
  ANC so far).
- Settings screen toggles (in-ear detection, low latency, personalized
  ANC, Ultra Bass) — wired and compiles, not confirmed round-tripping on
  real hardware.

## Debugging on device

```bash
adb logcat -c
adb logcat -s NothingX:V         # DirectRfcommTransport's protocol logs (watch or phone)
adb logcat -s NothingXRelay:V    # relay-path logs (PhoneRelayService, WearRelayTransport)
```

Full crash context: `adb logcat -s NothingX:V NothingXRelay:V AndroidRuntime:E`

## Test checklist for the user

**Regression pass (things that worked before this session):**
1. Install the new `wear-debug.apk`. Connect to your CMF Buds Pro 2 as usual.
2. Confirm ANC switching still works (off/ANC/transparency).
3. Confirm the Tile still opens/controls ANC as before.

**New: phone relay (needs a watch+phone pair actually paired via the Wear
OS companion app):**
1. Install `phone-debug.apk` on your phone, `wear-debug.apk` on the watch.
2. On the watch: Settings → toggle "Use phone relay" on → close and reopen
   the watch app (the toggle needs a restart to take effect, it'll tell you).
3. On the phone: open the Nothing X app, grant Bluetooth/notification
   permissions if prompted, tap your CMF Buds Pro 2 in the list to start
   relaying.
4. On the watch: try connecting/ANC switching — commands should now be
   going watch → phone → earbuds instead of watch → earbuds directly.
5. Report back: did the phone show "Connected — relaying to watch" in its
   notification? Did ANC switching from the watch actually change the
   earbuds' mode? Did the watch UI update (battery/ANC state) after
   changes happened?

If the relay doesn't work, `adb logcat -s NothingXRelay:V` on both devices
during a test will show exactly where it breaks (command never sent,
command sent but not received, command received but transport not
connected, etc.) — worth capturing that log if something's wrong.

## Next steps, in order

1. **Verify compileSdk 37 CI run is actually green** (commit `08fdc13`) —
   check before doing anything else in this area.
2. **Verify the phone relay on real hardware** (test checklist above) —
   highest priority now that it's implemented; either confirm it works or
   find out where it breaks.
3. **Build and retest the persistent-connection + in-tile-control
   architecture on device** — still unverified from a prior round.
4. **Phase 2: implement the actual Wear Widget** —
   `GlanceWearWidgetService`/`GlanceWearWidget` + RemoteCompose, modeled on
   `android/wear-os-samples/WearWidget`. Now unblocked (compileSdk 37 is
   in). Not started.
5. **Phase 3: Samsung Buds Controller APK review** for UX inspiration only
   (path: `/root/.claude/uploads/5565ec22-aa0e-5699-a5e5-28bd8e488111/7e83f748-com.samsung.android.watch.budscontroller_1.0.08.47-100800047_minAPI30nodpi_apkmirror.com.apk`
   — not guaranteed to exist in a fresh session/container). Not started.
6. Reconnect-on-boot / retry logic for `EarbudsConnectionService` if the
   earbuds go out of range while it's running in the background.
7. Gesture *editing*, custom EQ, CMF's separate Listening Mode command —
   see CLAUDE.md, still deferred from earlier passes.

## User's stated priorities (don't lose these across a compaction)

- Optimizations matter — not a literal battery-% target, just "don't be
  wasteful": proper foreground-service scoping, no busy-polling,
  Doze-aware. The relay path's DataClient-not-polling design is this
  applied for real.
- Don't worry about pre-Wear-OS-4 compatibility — Wear OS 4+ baseline is
  fine, latest is fine too.
- Never use Nothing's or Samsung's actual trademarked/compiled assets —
  Nothing X app icon/fonts/logo off-limits (CLAUDE.md's Icons and
  branding section); Samsung Buds Controller APK is UX inspiration only.
- User wants to be left running autonomously and given download links +
  changelog + a specific test checklist once there's something worth
  testing, rather than asked questions mid-task.

## Known limits

- No SDP-based channel discovery, probe-list only.
- Phone relay is implemented but unverified on real hardware.
- No model/SKU detection, so settings commands aren't gated per-device.
- No reconnect/retry if the background connection drops.
- Relay mode switch (Settings' "Use phone relay" toggle) needs an app
  restart to take effect — not live.
