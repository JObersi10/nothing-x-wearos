# HANDOFF

Last updated: 2026-09-21, toolchain-migration round (AGP 9.1.1 / Kotlin
2.2.10 / compileSdk 37, in progress toward real Wear Widgets).

## Where things stand right now

The persistent-connection + in-tile-control round (previous "last updated")
is CI-green and unchanged. On top of that, this round is a **toolchain-only**
migration (zero feature code) to unlock real Wear OS Widgets (Jetpack Glance
for Wear + RemoteCompose) instead of just the classic full-screen Tile —
see CLAUDE.md's "Toolchain: AGP 9.1.1 / Kotlin 2.2.10 / compileSdk 37"
section for the full cascade and why it touched nearly every build file.

Two pushes so far:
1. `e0cb6f8` — the toolchain bump itself. **Failed CI** on
   `:bluetooth:assembleDebug` with `Cannot add extension with name
   'kotlin'` — AGP 9's built-in Kotlin collided with this project's
   leftover explicit `org.jetbrains.kotlin.android` plugin application.
2. `4304d63` — the fix: dropped the explicit kotlin-android plugin from all
   three Android modules (built-in Kotlin now owns that), dropped the
   now-redundant `kotlinOptions{ jvmTarget }` blocks, bumped the CI
   `sdkmanager` step to `platforms;android-37`/`build-tools;36.0.0` to match
   the new compileSdk floor. `:protocol:test` reconfirmed passing locally
   (17 tests — the "24 tests" figure in an earlier version of this doc was
   wrong; check `protocol/src/test/` directly if this number drifts again).
   **CI result not yet confirmed as of this doc update** — check
   `https://github.com/JObersi10/nothing-x-wearos/actions` (workflow run
   for commit `4304d63`, branch `claude/ecstatic-galileo-evyo4w`) or PR
   `https://github.com/JObersi10/nothing-x-wearos/pull/1` directly before
   assuming green.

**No Wear Widget implementation exists yet** — this round is purely the
toolchain prerequisite. `NothingXTileService` (classic ProtoLayout Tile) is
unchanged and still the only interactive Tile/widget surface in the app.

## What's done

- **`protocol` module**: frame codec, CRC16/ARC, command IDs, `EarbudsSession`
  state machine. **Built and unit-tested for real** — 24 tests, all passing.
- **`bluetooth` module**: `DirectRfcommTransport` with `Log.d/i/w/e` under
  tag `NothingX` (gated behind `Log.isLoggable` on the hot RX/TX path — see
  "Debugging on device" below), `BondedDevices`, `NothingDeviceMatcher`.
- **`wear` module**: Compose app, now with a **persistent background
  connection** shared between the app and the Tile — see CLAUDE.md's "Tile
  now controls ANC without opening the app" section for the full
  architecture (`EarbudsConnectionHolder` singleton, `EarbudsConnectionService`
  foreground service, `TileActionActivity` trampoline). Device list filters
  to matched devices, no longer disconnects on navigation (that was removed
  — it conflicted with the persistent connection). Device detail: battery
  card first (icon replaced with letter badges L/C/R, not custom art),
  ANC card below, Settings chip. Settings screen: in-ear detection, low
  latency, personalized ANC, Ultra Bass with an 0-5 `InlineSlider`, Find My
  Earbuds + Ear Tip Fit Test with toast feedback, and now an explicit
  Disconnect action. Tile: edge-to-edge card, icon badge, and the 3 ANC dots
  are now real tap targets that change ANC mode without opening the app.
- **`phone` module**: scaffold only, explicitly not functional — the user
  has asked for the real Data Layer relay build next, not started yet.
- CI (`.github/workflows/ci.yml`), MIT `LICENSE` + `THIRD_PARTY_NOTICES.md`.

## What's verified vs. not

**Confirmed on real hardware (Galaxy Watch 4 + CMF Buds Pro 2, 2026-09-21):**
- Direct RFCOMM connect and ANC mode switching (off/ANC/transparency) work.
- CI is fully green through the settings-screen + Ultra Bass slider round.

**NOT yet verified — built after the last device round, before any
retest:**
- The entire persistent-connection + in-tile-control architecture
  (`EarbudsConnectionHolder`, `EarbudsConnectionService`,
  `TileActionActivity`, the Tile's per-dot click targets). CI can confirm it
  compiles; it cannot confirm the foreground service actually keeps the
  process alive in the background, that the notification shows (needs
  `POST_NOTIFICATIONS` grant — now requested via `PermissionGate`), that
  tapping a Tile dot actually changes ANC with the app closed, or that
  `ActionBuilders.AndroidActivity.addKeyToExtraMapping()` (used to pass the
  target mode to the trampoline activity) is the right API — first use of
  that specific call in this project.
- Battery and EQ round-trip on CMF specifically (user has only confirmed ANC).
- Settings screen toggles (in-ear detection, low latency, personalized ANC,
  Ultra Bass) — wired and compiles, not yet confirmed round-tripping on
  real hardware.

## Debugging on device

```bash
adb logcat -c
adb logcat -s NothingX:V      # DirectRfcommTransport's protocol logs
```

Full crash context: `adb logcat -s NothingX:V AndroidRuntime:E`

## Next steps, in order

1. **Confirm CI is green on the toolchain migration** (commit `4304d63` or
   whatever's latest on `claude/ecstatic-galileo-evyo4w` / PR #1) before
   layering any Wear Widget code on top. If red, diagnose from the actual
   job log (`bluetooth`/`wear`/`phone` `assembleDebug` steps), fix, push —
   don't touch feature code until this is clean, per the isolation plan.
2. **Phase 2: implement the actual Wear Widget** — `GlanceWearWidgetService`
   /`GlanceWearWidget` + RemoteCompose (`@RemoteComposable`, `RemoteBox`,
   `RemoteText`, etc.), modeled on Google's own
   `android/wear-os-samples/WearWidget` sample the user linked. Small (2×1)
   and large (2×2) sizes; manifest needs both
   `androidx.glance.wear.action.BIND_WIDGET_PROVIDER` and
   `androidx.wear.tiles.action.BIND_TILE_PROVIDER` (for the auto-fallback-
   to-Tile behavior on pre-Wear-OS-4 devices). Wire it to
   `EarbudsConnectionHolder` the same way `NothingXTileService` already is.
   Not started yet.
3. **Build and retest the persistent-connection + in-tile-control
   architecture on device** — still unverified since the last device round
   predates it. Confirm: normal app use still works (connect, ANC,
   settings), the foreground service notification appears, and a Tile tap
   actually changes ANC with the app fully closed.
4. **Phase 3: Samsung Buds Controller APK review** (path on the machine that
   received it:
   `/root/.claude/uploads/5565ec22-aa0e-5699-a5e5-28bd8e488111/7e83f748-com.samsung.android.watch.budscontroller_1.0.08.47-100800047_minAPI30nodpi_apkmirror.com.apk`
   — not guaranteed to exist in a fresh session/container) — UX/interaction-
   pattern inspiration only, per the same never-reuse-their-actual-assets
   rule as the Nothing trademark guidance. Not started.
5. **Phone relay** (`phone/` module) — user has explicitly asked for the
   real build: Wear Data Layer `MessageClient`/`ChannelClient`, a
   phone-side foreground service running the same `DirectRfcommTransport`
   logic, wired as a second `EarbudsTransport` implementation. Battery
   target: not a literal number, but built right — proper foreground
   service scoping, no busy-polling, Doze-aware, only wake the radio when
   there's something to send. User suggested surveying real open-source
   Android BLE/relay apps on GitHub for patterns first. Not started.
6. Reconnect-on-boot / retry logic for `EarbudsConnectionService` if the
   earbuds go out of range while it's running in the background — currently
   it just sits disconnected until the app or Tile is used again.
7. Gesture *editing*, custom EQ, CMF's separate Listening Mode command — see
   CLAUDE.md, all still deferred from earlier passes.

## User's stated priorities (don't lose these across a compaction)

- Optimizations matter — not a literal battery-% target, just "don't be
  wasteful": proper foreground-service scoping, no busy-polling, Doze-aware.
- Don't worry about pre-Wear-OS-4 compatibility at all anymore (this was
  relaxed from an earlier "keep the full-screen Tile for older watches"
  ask) — Wear OS 4+ baseline is fine, latest is fine too. If Glance-for-Wear
  forces `minSdk` up from 30, that's acceptable now, not a problem to route
  around.
- Never use Nothing's or Samsung's actual trademarked/compiled assets —
  Nothing X app icon/fonts/logo are off-limits (see CLAUDE.md's Icons and
  branding section), and the Samsung Buds Controller APK is UX inspiration
  only, never asset/code extraction.
- User is stepping away and asked to be left running autonomously: "keep on
  going... after you think u have everything, just give me the download
  links, change logs, and what u need from me in terms of what to press and
  test out." That means: once CI is confirmed green, pull the APK artifact
  download links from the workflow run and hand them over with a concise
  changelog and a specific test checklist (what to tap, what to confirm),
  not just "it builds."

## Known limits

- No SDP-based channel discovery, probe-list only.
- `phone/` module is not functional yet.
- No model/SKU detection, so settings commands aren't gated per-device.
- No reconnect/retry if the background connection drops.
