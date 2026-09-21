# HANDOFF

Last updated: 2026-09-21, persistent-connection + in-tile-control round.

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

1. **Build and retest on device.** This round touched connection lifecycle,
   the Tile, and the manifest (new service + activity + permissions) — a
   lot of surface area since the last confirmed-working build. Confirm:
   normal app use still works (connect, ANC, settings), the foreground
   service notification appears, and a Tile tap actually changes ANC with
   the app fully closed.
2. **Phone relay** (`phone/` module) — user has explicitly asked for the
   real build now: Wear Data Layer `MessageClient`/`ChannelClient`, a
   phone-side foreground service running the same `DirectRfcommTransport`
   logic, wired as a second `EarbudsTransport` implementation. Battery
   target: not a literal number, but built right — proper foreground
   service scoping, no busy-polling, Doze-aware, only wake the radio when
   there's something to send. Worth surveying a couple of real open-source
   Android BLE/Bluetooth relay or companion apps on GitHub first for
   established patterns (not started yet).
3. Reconnect-on-boot / retry logic for `EarbudsConnectionService` if the
   earbuds go out of range while it's running in the background — currently
   it just sits disconnected until the app or Tile is used again.
4. Gesture *editing*, custom EQ, CMF's separate Listening Mode command — see
   CLAUDE.md, all still deferred from earlier passes.

## Known limits

- No SDP-based channel discovery, probe-list only.
- `phone/` module is not functional yet.
- No model/SKU detection, so settings commands aren't gated per-device.
- No reconnect/retry if the background connection drops.
