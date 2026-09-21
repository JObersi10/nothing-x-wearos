# HANDOFF

Last updated: 2026-09-21, first build session.

## What's done

- **`protocol` module**: frame codec (`FrameEncoder`/`FrameParser`), CRC16/ARC,
  command IDs (battery/ANC/EQ/activation/wear-status/firmware/serial), and
  `EarbudsSession` (the protocol state machine, ported from something-x's
  `NothingDevice._dispatch_x55`). **Built and unit-tested for real** — 17
  tests, all passing, in this sandbox (`./gradlew :protocol:test`).
- **`bluetooth` module**: `DirectRfcommTransport` (watch-direct RFCOMM
  connection, channel probe, recv loop, command send), `BondedDevices`
  (paired-device listing), `NothingDeviceMatcher` (Nothing/CMF name
  matching). Written, **not built** — see "What's not verified" below.
- **`wear` module**: Compose app — device list screen, device detail screen
  (pill-shaped ANC selector styled after the Galaxy Wearable app screenshots
  the user gave as inspiration, EQ preset chips, battery text), permission
  gate, DataStore-backed prefs, `DeviceViewModel`, and a quick-glance Tile
  (`NothingXTileService`, read-only v1, styled after the "rounded pill"
  reference image). Written, **not built**.
- **`phone` module**: scaffold only, explicitly not functional. See its
  `MainActivity` doc comment.
- Repo scaffolding: Gradle wrapper (generated in-sandbox, Gradle 8.7),
  `.gitignore`, `local.properties.example`, GitHub Actions CI
  (`.github/workflows/ci.yml`), MIT `LICENSE` + `THIRD_PARTY_NOTICES.md` for
  the AGPLv3-sourced icons.

## What's verified vs. not

**Verified, for real, in this session:**
- `protocol` module compiles and all 17 unit tests pass on JDK 21 via
  Gradle 8.14.3 (this sandbox's toolchain).
- `dl.google.com` is network-blocked in this sandbox by policy — confirmed
  via the agent-proxy status endpoint, not assumed. This is *why* the Android
  modules are unbuilt here, not a project defect.

**NOT verified — needs a machine with a real Android SDK and internet:**
- Whether `wear`, `bluetooth`, and `phone` actually compile. They're written
  carefully and the protocol layer under them is solid, but zero Android
  compiler feedback has touched this code yet. Expect at least a few
  small build errors (dependency version mismatches, ProtoLayout/Tiles API
  surface details — that API changes between library versions and I was
  writing against my best recollection of `androidx.wear.tiles`/
  `androidx.wear.protolayout` 1.4.0/1.2.0's shape, not a live compiler).
- Whether `DirectRfcommTransport`'s reflection-based
  `createRfcommSocket(int)` call works on Wear OS at all — this is the
  central hardware risk, see `CLAUDE.md`'s note on it. Nothing here confirms
  a Wear OS watch can open a Classic RFCOMM socket to a *third* device (not
  its paired phone).
- Whether the channel-probe list, activation handshake, and command IDs
  actually get a real Nothing Ear device to respond over Android's BT stack
  — they're a faithful port of something-x's confirmed-working Linux
  implementation, but Android's Bluetooth stack behaves differently in
  plenty of subtle ways from BlueZ.
- CMF Buds command IDs — unverified in the *source* material this was ported
  from, let alone by us.

## Next steps, in order

1. **Build it.** `cp local.properties.example local.properties`, point at the
   SABRENT SDK, run `./gradlew :wear:assembleDebug`. Fix whatever compile
   errors show up — expect some in the Tile code especially.
2. **The one test that actually matters first**: `adb install` the debug APK
   to the Galaxy Watch 4, pair Nothing earbuds to the *watch itself* (not
   just the phone), and see if `DirectRfcommTransport.connect()` gets past
   the channel probe to a real `0x55` response. If it never does, the whole
   direct-connect architecture needs to fall back to the phone-relay path —
   that's a real possible outcome, not just a formality.
3. If direct connect works: verify battery/ANC/EQ round-trip against real
   hardware, then wire up the raw-frame debug logging mentioned in
   `CLAUDE.md` before touching CMF hardware.
4. If direct connect doesn't work: `phone/` needs its actual Data Layer
   relay implementation — currently just a placeholder module.
5. Once the core loop works: v2 features from ear-web's larger command set,
   in-tile quick actions, launcher icon/mipmap set (currently reusing the
   earbuds drawable as the app icon, no adaptive icon).

## Known limits

- No adaptive launcher icon — `ic_earbuds.xml` is used directly as
  `android:icon`, which works but isn't a proper mipmap set.
- Tile has no live connection — see `CLAUDE.md`.
- No SDP-based channel discovery, probe-list only (something-x tries SDP
  first as a priority hint; skipped here for v1 simplicity — see
  `DirectRfcommTransport`'s doc comment).
