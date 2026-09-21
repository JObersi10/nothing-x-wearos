package com.nothingx.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class Crc16Test {
    @Test
    fun `crc of empty input is init value`() {
        assertEquals(0xFFFF, Crc16.compute(ByteArray(0)))
    }

    @Test
    fun `crc of a known GET_PROTO_VERSION probe frame`() {
        // header for GET_PROTO_VERSION probe with a 1-byte payload [0x01], fsn=1,
        // as something-x sends it: SOF, ctrl LE, cmd LE, len LE, fsn, payload
        val header = byteArrayOf(
            0x55, 0x60, 0x01, // SOF, ctrl=0x0160 LE
            0x01.toByte(), 0xC0.toByte(), // cmd=0xC001 LE
            0x00, 0x00, // len=0
            0x01, // fsn
        )
        val payload = byteArrayOf(0x01)
        val crc = Crc16.compute(header + payload)
        // Re-derive independently via the bitwise definition to catch transcription errors.
        var expected = 0xFFFF
        for (b in header + payload) {
            expected = expected xor (b.toInt() and 0xFF)
            repeat(8) {
                expected = if (expected and 1 != 0) (expected ushr 1) xor 0xA001 else expected ushr 1
            }
        }
        assertEquals(expected, crc)
    }
}
