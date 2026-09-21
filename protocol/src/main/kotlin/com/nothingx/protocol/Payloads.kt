package com.nothingx.protocol

/**
 * Decoders for `[count:1][type:1][val:1]...` style payloads shared by battery,
 * ANC, and earphone-status frames. Ported from something-x's `_parse_*` methods.
 */
internal object Payloads {

    /** battery: val byte is bit7=charging, bits[6:0]=percent. Returns type -> percent. */
    fun parseBattery(payload: ByteArray): Map<Int, Int> {
        if (payload.size < 3) return emptyMap()
        val count = payload[0].toInt() and 0xFF
        val out = mutableMapOf<Int, Int>()
        var i = 1
        var read = 0
        while (read < count && i + 1 < payload.size) {
            val type = payload[i].toInt() and 0xFF
            val value = payload[i + 1].toInt() and 0xFF
            out[type] = value and 0x7F
            i += 2
            read++
        }
        return out
    }

    /** ANC: [type:1][value:1][pad:1] triplets. type=1 mode, type=2 level. */
    fun parseAnc(payload: ByteArray): AncSnapshot? {
        if (payload.size < 3) return null
        var mode: AncMode? = null
        var level: Int? = null
        var i = 0
        while (i <= payload.size - 3) {
            val type = payload[i].toInt() and 0xFF
            val value = payload[i + 1].toInt() and 0xFF
            when (type) {
                1 -> mode = AncMode.fromModeWireValue(value)
                2 -> level = value
            }
            i += 3
        }
        if (mode == null && level == null) return null
        return AncSnapshot(mode, level)
    }

    /** earphone status: type 2=left, 3=right, 6=stereo. bit2 of val = worn/in-ear. */
    fun parseEarphoneStatus(payload: ByteArray): Map<Int, Boolean> {
        if (payload.size < 3) return emptyMap()
        val count = payload[0].toInt() and 0xFF
        val out = mutableMapOf<Int, Boolean>()
        var i = 1
        var read = 0
        while (read < count && i + 1 < payload.size) {
            val type = payload[i].toInt() and 0xFF
            val value = payload[i + 1].toInt() and 0xFF
            out[type] = (value and 0x04) != 0
            i += 2
            read++
        }
        return out
    }
}

data class AncSnapshot(val mode: AncMode?, val level: Int?)
