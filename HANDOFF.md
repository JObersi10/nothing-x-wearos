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
2. ~~Add the settings screen (in-ear detection, low latency, personalized
   ANC, bass enhance, find-my-earbuds, ear fit test, gesture count)~~ — done
   this round, real mined commands (see CLAUDE.md), **not yet built/run**.
3. ~~Build and verify this round's changes~~ — CI confirms `SettingsScreen.kt`
   (including `ToggleChip`, used for the first time here) compiles clean.
   Still needs an actual on-device test of the new settings toggles — CI
   only proves it compiles, not that in-ear detection/low latency/
   personalized ANC/bass enhance round-trip correctly on real hardware.
4. Confirm the newly-wired settings actually round-trip on CMF Buds Pro 2 —
   only ANC has been confirmed on real hardware so far; battery/EQ/settings
   are all still unconfirmed on CMF specifically.
5. Gesture *editing* (not just the read-only count) — the per-gesture array
   structure (`gestureDevice`/`gestureCommon`/`gestureType`/`gestureAction`)
   needs real UI design, deferred from this pass.
6. Custom EQ, CMF's separate Listening Mode command — see CLAUDE.md.
7. Tile is still read-only (no live RFCOMM connection of its own) — v2 item
   is either a bound background service or the phone-relay transport wired
   to a Tile action for in-tile quick toggling.

## Known limits

- Tile has no live connection, no in-tile quick actions yet.
- No SDP-based channel discovery, probe-list only.
- `phone/` module is not functional — see its `MainActivity` doc comment.
- No model/SKU detection, so settings commands aren't gated per-device the
  way ear-web gates them (e.g. personalized ANC is Ear (2)-only in the
  official app) — sending an unsupported command is assumed harmless but
  that's inherited from the source material, not separately verified.
