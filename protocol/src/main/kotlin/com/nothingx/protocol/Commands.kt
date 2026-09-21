package com.nothingx.protocol

/**
 * Command IDs for the Nothing Ear "0x55" RFCOMM protocol.
 *
 * Source: reverse-engineered from the official Nothing Android APK by the
 * something-x (github.com/SoaOaoS/something-x) and ear-web
 * (github.com/radiance-project/ear-web) projects. This module ports the
 * subset something-x confirmed working end-to-end on real Nothing Ear (2)
 * hardware — battery, ANC, EQ, activation, wear status, firmware/serial.
 *
 * The settings-screen command IDs below (in-ear detection, low-latency mode,
 * personalized ANC, gestures, bass enhance, advanced/custom EQ, listening
 * mode, find-my-earbuds/ring, LED case color, ear fit test) were mined
 * directly from ear-web/res/js/bluetooth_socket.js's `send(command, ...)`
 * calls — every command there is a literal decimal command ID this app's
 * confirmed-working `send()` builds into the exact same frame format
 * `EarbudsSession`/`FrameEncoder` implement, so these are as trustworthy as
 * the core set above, not guesses. Model gating (some commands only apply to
 * specific SKUs, e.g. personalized ANC is Ear (2)-only) is NOT enforced
 * here — this app doesn't fetch the model/SKU field the way ear-web reads it
 * from `GET_REMOTE_CONF`'s CSV payload, only the serial number field.
 * Sending an unsupported command is expected to be a harmless no-op, same
 * as something-x's tolerant handling of unknown commands, but that's an
 * assumption inherited from the source material, not separately verified.
 *
 * Frame layout (both directions):
 *   [SOF:1=0x55][ctrl:2 LE][cmd:2 LE][len:2 LE][fsn:1][payload:len][crc:2 if ctrl&0x20]
 *
 * All outgoing frames use ctrl=0x0160 (CRC + multiFrames + deviceType=1).
 * TX sends the raw 16-bit cmd ID as-is. RX responses have bit 15 cleared;
 * callers normalize with `received_cmd or 0x8000` to match against these IDs.
 */
object Commands {
    const val SOF: Int = 0x55
    const val CTRL_HOST_CRC: Int = 0x0160

    // Query commands (0xC0xx) - app -> device, response has bit15 cleared
    const val GET_PROTO_VERSION: Int = 0xC001 // activation handshake
    const val GET_REMOTE_CONF: Int = 0xC006 // serial number (UTF-8 string)
    const val GET_BATTERY: Int = 0xC007
    const val GET_EARPHONE_STATUS: Int = 0xC00A
    const val GET_NOISE_REDUCTION: Int = 0xC01E // payload [0x03] = request 3 entries
    const val GET_EQ_MODE: Int = 0xC01F
    const val GET_HOST_VERSION: Int = 0xC042 // firmware version (UTF-8 string)

    // Settings-screen query commands, mined from ear-web (see class doc)
    const val GET_IN_EAR: Int = 0xC00E
    const val GET_LATENCY: Int = 0xC041
    const val GET_LED_CASE_COLOR: Int = 0xC017 // Ear (1) / B181 only
    const val GET_GESTURE: Int = 0xC018
    const val GET_PERSONALIZED_ANC: Int = 0xC020 // Ear (2) / B155 only
    const val GET_CUSTOM_EQ: Int = 0xC044
    const val GET_ADVANCED_EQ: Int = 0xC04C
    const val GET_ENHANCED_BASS: Int = 0xC04E
    const val GET_LISTENING_MODE: Int = 0xC050 // CMF's EQ-equivalent (B172/B168), NOT the same as GET_EQ_MODE

    // SET commands (0xF0xx) - app -> device, ACK has bit15 cleared
    const val SET_ACTIVATED: Int = 0xF001 // activation response, no payload
    const val RING_BUDS: Int = 0xF002 // find-my-earbuds
    const val SET_GESTURE: Int = 0xF003
    const val SET_IN_EAR: Int = 0xF004
    const val SET_LED_CASE_COLOR: Int = 0xF00D // Ear (1) / B181 only
    const val SET_NOISE_REDUCTION: Int = 0xF00F // payload: [0x01, anc_val, 0x00]
    const val SET_EQ: Int = 0xF010 // payload: [eq_val]
    const val SET_PERSONALIZED_ANC: Int = 0xF011 // Ear (2) / B155 only
    const val LAUNCH_EAR_FIT_TEST: Int = 0xF014 // payload: [0x01]
    const val SET_LISTENING_MODE: Int = 0xF01D // CMF's EQ-equivalent SET (B172/B168)
    const val SET_LATENCY: Int = 0xF040
    const val SET_CUSTOM_EQ: Int = 0xF041 // complex float-encoded payload, not implemented here
    const val SET_ADVANCED_EQ_ENABLED: Int = 0xF04F
    const val SET_ENHANCED_BASS: Int = 0xF051 // payload: [enabled, level*2]

    // Device event notifications (0xE0xx) - device -> app, bit15 always set
    const val EVT_BATTERY: Int = 0xE001
    const val EVT_STATUS: Int = 0xE002
    const val EVT_NOISE_REDUCTION: Int = 0xE003
    const val EVT_EAR_FIT_TEST_RESULT: Int = 0xE00D

    /** Normalize a raw RX command id (bit15 cleared by the device) to the request id it answers. */
    fun normalizeRx(rawCmd: Int): Int = rawCmd or 0x8000

    // Battery payload entry types: [count:1][type:1][val:1]... pairs
    const val BATTERY_LEFT: Int = 2
    const val BATTERY_RIGHT: Int = 3
    const val BATTERY_CASE: Int = 4
    const val BATTERY_STEREO: Int = 6 // single-unit devices (e.g. Nothing Headphone (1))

    // ANC wire values for SET_NOISE_REDUCTION payload byte [1] (MODE constants,
    // not the VALUE constants used by type=2 level entries).
    const val ANC_WIRE_OFF: Int = 5
    const val ANC_WIRE_STRONG: Int = 1
    const val ANC_WIRE_MEDIUM: Int = 2
    const val ANC_WIRE_WEAK: Int = 3
    const val ANC_WIRE_TRANSPARENCY: Int = 7
}
