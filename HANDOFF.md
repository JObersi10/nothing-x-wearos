# HANDOFF

Last updated: 2026-09-21, first build session + first real-hardware round.

## What's done

- **`protocol` module**: frame codec, CRC16/ARC, command IDs (battery/ANC/EQ/
  activation/wear-status/firmware/serial), `EarbudsSession` state machine.
  **Built and unit-tested for real** — 17 tests, all passing.
- **`bluetooth` module**: `DirectRfcommTransport` (watch-direct RFCOMM
  connection, channel probe, recv loop, command send — now with `Log.d/i/w/e`
  under tag `NothingX` throughout, see "Debugging on device" below),
  `BondedDevices`, `NothingDeviceMatcher`.
- **`wear` module**: Compose app — device list (now filters to
  Nothing/CMF-matched devices by default, with a "show all paired devices"
  fallback chip), device detail screen (ANC selector, battery text; EQ UI
  removed for now, not a priority — the protocol plumbing is still there), a
  Tile (`NothingXTileService`) styled as an edge-to-edge rounded card with an
  icon badge and a 3-dot ANC mode indicator.
- **`phone` module**: scaffold only, explicitly not functional.
- CI (`.github/workflows/ci.yml`), MIT `LICENSE` + `THIRD_PARTY_NOTICES.md`.

## What's verified vs. not

**Confirmed on real hardware (Galaxy Watch 4 + CMF Buds Pro 2, 2026-09-21):**
- `DirectRfcommTransport`'s reflection-based RFCOMM channel connect works on
  Wear OS 3. This was the single biggest open architectural risk and it's
  resolved — the direct-connect design is viable, not just theoretical.
- ANC mode switching (off / ANC / transparency) round-trips correctly over
  this connection on a CMF Buds Pro 2.
- CI is fully green: `protocol`, `bluetooth`, `wear`, and `phone` all build
  against the real Android SDK (GitHub Actions), not just this sandbox's
  best-effort read of API surfaces.

**Still unconfirmed:**
- Battery and EQ round-trip on CMF specifically (user has only reported on
  ANC so far).
- Whether the channel-probe list and activation handshake work the same way
  on other Nothing/CMF models, or other watches.
- CMF Buds command IDs beyond what's now confirmed working (ANC) — battery/
  EQ/wear-status on CMF still ride on the "probably same as Nothing, unverified"
  assumption from something-x/ear-web.

## Debugging on device

`DirectRfcommTransport` logs everything (channel probe attempts, TX/RX frame
bytes, connection state) under tag `NothingX`. To watch it live:

```bash
adb logcat -c                # clear old logs first
adb logcat -s NothingX:V      # follow only this app's protocol logs
```

If you need full context around a crash (not just the protocol logs):

```bash
adb logcat -s NothingX:V AndroidRuntime:E
```

## Next steps, in order

1. ~~Build it, confirm direct RFCOMM connect works on real hardware~~ — done.
2. Confirm battery and EQ round-trip on CMF Buds Pro 2 (EQ UI is currently
   hidden but `viewModel.setEqPreset()` still works if called).
3. **Gestures/touch controls** — user asked for this (matching Samsung Buds
   Controller's touch-control toggle). Nothing Ear does support this per
   ear-web's `sendGetGesture()`/gesture-set commands, but those command IDs
   were never mined into `protocol/Commands.kt` (v1 scope was deliberately
   core-only). Next real feature to add: pull the actual command bytes from
   `ear-web/res/js/bluetooth_socket.js` and `control.js`, port them the same
   way `protocol`'s existing commands were ported from something-x, and wire
   up a gestures screen.
4. **Explicitly out of scope** — flagged to the user directly: Spatial
   Audio/360/head tracking and Bixby voice commands are Samsung Galaxy Buds
   features with no Nothing/CMF protocol equivalent in either source repo.
   Don't build fake toggles for these.
5. Tile is still read-only (no live RFCOMM connection of its own) — v2 item
   is either a bound background service or the phone-relay transport wired
   to a Tile action for in-tile quick toggling.
6. Launcher icon is still `ic_earbuds.xml` reused directly, no adaptive
   mipmap set.

## Known limits

- No adaptive launcher icon.
- Tile has no live connection, no in-tile quick actions yet.
- No SDP-based channel discovery, probe-list only.
- `phone/` module is not functional — see its `MainActivity` doc comment.
