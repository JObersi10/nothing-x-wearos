# HANDOFF

Last updated: 2026-09-22. Responding to the user's first real hardware pass
(six bugs reported: auto-open timing, missing icons, dead Find My Earbuds,
stale "Connected" status, endless reconnect notifications, stuck battery,
plus phone relay not working/confusing). All six addressed in code this
round. **CI is confirmed green** on commit `2f41740` (protocol tests,
bluetooth build, wear debug APK, phone debug APK all passed — see APK
downloads below for the run). **None of it has touched real hardware
yet** — this is the thing to check next, see the test checklist below.

## Where things stand right now

### Root cause behind three of the six reports

Stale "Connected" status, dead Find My Earbuds, and stuck battery all traced
to one bug: `DirectRfcommTransport`'s blocking socket read doesn't reliably
notice a real disconnect promptly, so the app kept reporting "Connected"
long after the earbuds were actually gone — nothing re-queried battery,
nothing reconnected, commands silently no-op'd on a dead socket. Fixed by
listening for `BluetoothDevice.ACTION_ACL_DISCONNECTED` and force-closing
the socket the instant it fires for the exact device. This lives in
`:bluetooth`'s `DirectRfcommTransport`, so it fixes both the watch's direct
connect AND the phone relay's own instance of the same class at once.

Paired with that: `EarbudsConnectionHolder` now has bounded, backed-off
reconnect (5s/15s/30s/60s, then stops and needs an explicit retry) — it had
*no* reconnect logic at all before, which combined with the above to explain
the stale-forever "Connected" state. Bounded specifically so this doesn't
become the "endless notifications" bug's replacement.

### The other three

- **Auto-open timing**: `MainActivity` now navigates to the device detail
  screen from a `LaunchedEffect` on `connectionState`, only once it's
  actually `Connected` — not the moment a tap or app-open starts a
  connection attempt. The app also now auto-resumes the last-connected
  device on open (`DeviceViewModel.init`), so opening the app behaves like
  "the earbuds are just there" instead of starting from a blank list.
- **Icons**: `ic_earbuds.xml` existed but was never placed in any Compose
  screen. Added as a leading icon on every device-list chip and a header
  icon on the detail screen.
- **Find My Earbuds**: no code bug found in the command itself — most likely
  the same stale-connection root cause (dropped silently on a socket the app
  thought was live). Also added raw RX frame logging (DEBUG level) so a
  repro capture can confirm or rule this out for real if still broken.

### Phone relay — rewritten, not just tweaked

The first hardware round found the relay "confusingly made" and not
working. Rewritten around "tap Buds (phone) on the watch, no phone
interaction needed":
- `PhoneRelayService` is now a `WearableListenerService` (not a plain
  `Service`) — Play Services wakes the phone app's process on an incoming
  relay command even if the phone app was never opened. Confirmed via a live
  search against Android's own docs that this is the current, non-deprecated
  mechanism (`MESSAGE_RECEIVED` intent-filter; the older `BIND_LISTENER`
  pattern is deprecated).
- New `AutoRelayReceiver` starts the relay proactively the moment the
  phone's own Bluetooth connects to a matched earbuds device — no watch or
  phone interaction at all needed for the common case.
- The watch no longer needs to know a real Bluetooth MAC to relay to — a
  blank `CMD_CONNECT` means "use whatever matched device the phone already
  has bonded," resolved phone-side.
- The Settings "Use phone relay" toggle (and restart-required
  `TransportModePrefs`) are gone. `EarbudsConnectionHolder` now holds both
  transports at once and exposes one stable pair of state flows that
  forward from whichever is active — relay vs. direct is a normal tap in
  the device list (`Buds (phone)`, always shown) now, not a global setting.

Full detail on all of the above: CLAUDE.md's "First real hardware feedback
round" and "`phone/` module — rewritten for real automatic use" sections.

## APK downloads

**Confirmed-green run** (commit `2f41740`, this round's full bug-fix pass +
phone relay rewrite):
https://github.com/JObersi10/nothing-x-wearos/actions/runs/35591724120

Direct artifact links (same run):
- wear-debug-apk: https://github.com/JObersi10/nothing-x-wearos/actions/runs/35591724120/artifacts/10635171025
- phone-debug-apk: https://github.com/JObersi10/nothing-x-wearos/actions/runs/35591724120/artifacts/10634444807

GitHub artifact downloads need you logged into GitHub in the browser (the
API zip URLs need an auth token) — open the run page (or the links above)
and download from there. Artifacts expire ~90 days after the run
(2026-12-20). Install via `adb install -r wear-debug.apk` after unzipping
(same for `phone-debug.apk`), or sideload however you normally do.

## What's done

- **`protocol`**: frame codec, CRC16/ARC, command IDs, `EarbudsSession`
  state machine. Unit-tested — 17 tests, all green (`protocol/src/test/`).
- **`bluetooth`**: `DirectRfcommTransport` (now with ACL-disconnect
  detection + raw frame debug logging), `BondedDevices`,
  `NothingDeviceMatcher`, `relay/RelayProtocol.kt`.
- **`wear`**: Compose app with a persistent background connection shared
  between the app and the Tile. Device list shows an earbuds icon per row
  plus an always-present `Buds (phone)` relay entry. Device detail shows an
  earbuds icon, battery card, ANC card, Settings chip. Auto-navigates to
  detail only once actually connected; auto-resumes the last device on app
  open. Bounded reconnect on unexpected disconnect. Settings: in-ear
  detection, low latency, personalized ANC, Ultra Bass slider, Find My
  Earbuds, Ear Tip Fit Test, Disconnect (the relay toggle is gone — picking
  relay is now a device-list tap). Tile: 3 ANC dots are real tap targets.
- **`phone`**: `PhoneRelayService` is now a `WearableListenerService` woken
  by Play Services on demand; `AutoRelayReceiver` starts it automatically on
  a real Bluetooth connect. `MainActivity` is now a status view + manual
  override, not a required step.
- CI (`.github/workflows/ci.yml`), MIT `LICENSE` + `THIRD_PARTY_NOTICES.md`.

## What's verified vs. not

**Confirmed on real hardware (Galaxy Watch 4 + CMF Buds Pro 2, before this
round):**
- Direct RFCOMM connect and ANC mode switching (off/ANC/transparency) work
  — this was true before the bugs reported in this round, and this round's
  fixes shouldn't have touched that core path, but re-confirm it in the
  regression pass below since several files it depends on did change.

**NOT yet verified — everything in this round:**
- All six bug fixes above — none have touched hardware yet.
- The rewritten phone relay path, start to finish (auto-start-on-ACL-connect,
  wake-on-message, blank-address auto-pick).
- Whether ACL-disconnect detection actually fires promptly on real hardware
  (the theory is solid — blocking socket reads not noticing disconnects
  quickly is a known Android BT quirk — but it's a theory until confirmed).
- Battery and EQ round-trip on CMF specifically (user has only confirmed
  ANC so far, from before this round).
- Settings screen toggles (in-ear detection, low latency, personalized ANC,
  Ultra Bass) — wired and compiles, not confirmed round-tripping on real
  hardware.

## Debugging on device

```bash
adb logcat -c
adb logcat -s NothingX:V         # DirectRfcommTransport's protocol logs (watch or phone)
adb logcat -s NothingXRelay:V    # relay-path logs (PhoneRelayService, WearRelayTransport, AutoRelayReceiver)
```

Full crash context: `adb logcat -s NothingX:V NothingXRelay:V AndroidRuntime:E`

Raw RX frame bytes now log at DEBUG level too (new this round) — useful if
Find My Earbuds or anything else still looks broken after this pass; capture
this log during a repro rather than guessing at the payload further.

## Test checklist for the user

**Regression pass (make sure nothing broke):**
1. Install the new `wear-debug.apk`. Connect to your CMF Buds Pro 2.
2. Confirm ANC switching still works (off/ANC/transparency).
3. Confirm the Tile still opens/controls ANC as before.
4. Confirm the earbuds icon now shows up on the device list and detail screen.

**The six reported bugs — check each one specifically:**
1. **Auto-open timing**: open the app with the earbuds already connected in
   the background — it should jump straight to the earbuds screen. Tap a
   device from the list — it should show "Connecting to X…" and only jump to
   the detail screen once actually connected, not immediately.
2. **Icons**: should now be visible on both the device list and detail screen.
3. **Find My Earbuds**: try it from Settings. If still silent, capture
   `adb logcat -s NothingX:V` during the attempt.
4. **Stale "Connected" after BT disconnect**: connect, then disconnect the
   earbuds from Bluetooth settings directly (not from this app) — the app
   should notice within a few seconds and show "Disconnected", not sit on
   "Connected" indefinitely.
5. **Endless reconnect notifications**: leave the earbuds out of range /
   powered off for a while with the app's background service running —
   reconnect attempts should stop after a few tries (roughly 2 minutes of
   backoff), not continue indefinitely.
6. **Battery staleness**: after a reconnect (from #4 or #5), confirm the
   battery percentages actually refresh rather than showing the old values.

**Phone relay (needs a watch+phone pair actually paired via the Wear OS
companion app) — now try the automatic path first:**
1. Install `phone-debug.apk` on your phone, `wear-debug.apk` on the watch.
   Open the phone app once to grant Bluetooth/notification permissions (it
   won't need to stay open after that).
2. With the phone app fully closed (swipe it out of recents), connect your
   earbuds to the phone via normal Bluetooth — the phone should start
   relaying on its own within a few seconds (check for the "Nothing X relay"
   notification). This is the fully-automatic path — no watch or phone
   interaction.
3. Separately, from the watch's device list, tap `Buds (phone)` — this
   should start relaying even if the phone app was never opened (tests the
   `WearableListenerService` wake-up specifically).
4. From the watch: try connecting/ANC switching — commands should go
   watch → phone → earbuds.
5. Report back: did the phone's notification say "Connected — relaying to
   watch"? Did ANC switching from the watch actually change the earbuds'
   mode? Did the watch UI update afterward? Did step 2 (fully automatic,
   phone app closed) actually work, or only step 3 (watch-triggered wake-up)?

If the relay doesn't work, `adb logcat -s NothingXRelay:V` on both devices
during a test will show exactly where it breaks — worth capturing if
something's wrong, especially to tell steps 2 vs. 3 apart.

## Next steps, in order

1. **This round's hardware verification** (test checklist above) — six bug
   fixes plus a relay rewrite, all unverified until confirmed on-device.
2. **Phase 2: implement the actual Wear Widget** —
   `GlanceWearWidgetService`/`GlanceWearWidget` + RemoteCompose, modeled on
   `android/wear-os-samples/WearWidget`. Unblocked since compileSdk 37
   landed. Not started.
3. **Phase 3: Samsung Buds Controller APK review** for UX inspiration only
   (path: `/root/.claude/uploads/5565ec22-aa0e-5699-a5e5-28bd8e488111/7e83f748-com.samsung.android.watch.budscontroller_1.0.08.47-100800047_minAPI30nodpi_apkmirror.com.apk`
   — not guaranteed to exist in a fresh session/container). Not started.
4. Gesture *editing*, custom EQ, CMF's separate Listening Mode command —
   see CLAUDE.md, still deferred from earlier passes.

## User's stated priorities (don't lose these across a compaction)

- Optimizations matter — not a literal battery-% target, just "don't be
  wasteful": proper foreground-service scoping, no busy-polling,
  Doze-aware, bounded (not endless) retry. The relay path's
  DataClient-not-polling design and this round's bounded reconnect are both
  this applied for real.
- Don't worry about pre-Wear-OS-4 compatibility — Wear OS 4+ baseline is
  fine, latest is fine too.
- Never use Nothing's or Samsung's actual trademarked/compiled assets —
  Nothing X app icon/fonts/logo off-limits (CLAUDE.md's Icons and
  branding section); Samsung Buds Controller APK is UX inspiration only.
- User wants to be left running autonomously and given download links +
  changelog + a specific test checklist once there's something worth
  testing, rather than asked questions mid-task.
- Real hardware feedback takes priority over further unverified feature
  work — this round is entirely a response to the user's first hardware
  pass, not new features.

## Known limits

- No SDP-based channel discovery, probe-list only.
- Phone relay is rewritten but still unverified on real hardware.
- No model/SKU detection, so settings commands aren't gated per-device.
- Reconnect is bounded (stops after ~2 minutes of backoff) — a device left
  out of range longer than that needs a manual retry (reopen the app, or
  tap the device again).
- ACL-disconnect detection is a system-broadcast-based theory, not yet
  hardware-confirmed to fire promptly in practice.
