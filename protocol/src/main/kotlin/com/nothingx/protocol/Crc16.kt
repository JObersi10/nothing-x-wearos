package com.nothingx.protocol

/**
 * CRC16/ARC (IBM), init=0xFFFF, poly=0xA001 (reflected 0x8005).
 * Matches something-x's `_crc16` and ear-web's `crc16()` — both reverse-engineered
 * from the Nothing Android APK, and both agree on this algorithm.
 */
object Crc16 {
    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
            }
        }
        return crc and 0xFFFF
    }
}
