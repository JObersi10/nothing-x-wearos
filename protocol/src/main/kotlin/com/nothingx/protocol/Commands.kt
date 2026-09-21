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
 * ear-web's command set is larger (gestures, advanced EQ, personalized ANC,
 * find-my-earbuds, in-ear detection toggle, low-latency mode, bass enhance)
 * but wasn't mined into typed constants here — v1 scope is core only. See
 * ear-web/res/js/bluetooth_socket.js and control.js for those command IDs
 * when v2 picks this up.
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

    // SET commands (0xF0xx) - app -> device, ACK has bit15 cleared
    const val SET_ACTIVATED: Int = 0xF001 // activation response, no payload
    const val SET_NOISE_REDUCTION: Int = 0xF00F // payload: [0x01, anc_val, 0x00]
    const val SET_EQ: Int = 0xF010 // payload: [eq_val]

    // Device event notifications (0xE0xx) - device -> app, bit15 always set
    const val EVT_BATTERY: Int = 0xE001
    const val EVT_STATUS: Int = 0xE002
    const val EVT_NOISE_REDUCTION: Int = 0xE003

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
