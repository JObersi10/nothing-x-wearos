# Third-party notices

## Icons adapted from ear-web (AGPLv3)

The following files under `wear/src/main/res/drawable/` are adapted (SVG path
data ported to Android VectorDrawable XML, otherwise unmodified) from
[radiance-project/ear-web](https://github.com/radiance-project/ear-web),
originally by RapidZapper and Bendix, licensed under the **GNU Affero General
Public License v3.0**:

- `ic_anc_off.xml` — from `res/assets/anc_off_icon.svg`
- `ic_anc_on.xml` — from `res/assets/anc_on_icon.svg`
- `ic_anc_transparency.xml` — from `res/assets/anc_transparent_icon.svg`
- `ic_arrow_right.xml` — from `res/assets/arrow_right.svg`
- `ic_back.xml` — from `res/assets/back.svg`

**What this means practically:** these five files remain under AGPLv3, not
this repo's MIT license (see the notice at the bottom of `LICENSE`). If you
sideload this app to your own devices for personal use, that's outside AGPLv3's
"conveying"/"remote network interaction" triggers and there's nothing to do.
If you ever distribute the built APK beyond your own devices, or run any part
of this project as a network service, AGPLv3 requires making the corresponding
source available to those users — practically speaking, just keep this repo
public and note where the source is, same as ear-web itself does.

All other icons in `wear/src/main/res/drawable/` (`ic_earbuds.xml`) are
original to this project.

## Protocol reverse-engineering credit

The `protocol` module's command IDs, frame format, and CRC algorithm are not
copied code but are re-derived from public documentation in two open-source
projects that reverse-engineered the Nothing Android APK independently:

- [SoaOaoS/something-x](https://github.com/SoaOaoS/something-x) (MIT) —
  primary source for the confirmed-working `0x55` protocol subset this app
  implements (battery, ANC, EQ, activation handshake, wear status, firmware/
  serial).
- [radiance-project/ear-web](https://github.com/radiance-project/ear-web)
  (AGPLv3) — corroborates the same protocol and documents a larger command
  set (gestures, advanced EQ, personalized ANC, find-my-earbuds, etc.) not
  yet implemented here.

Credit and thanks to both projects' authors for the reverse-engineering work.
