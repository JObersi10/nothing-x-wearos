package com.nothingx.protocol

/** A decoded `0x55`-protocol frame: normalized command id + raw payload. */
data class Frame(val cmd: Int, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Frame) return false
        return cmd == other.cmd && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * cmd + payload.contentHashCode()
}

/** Encodes a single outgoing `0x55` frame with CRC16 appended, as the device requires. */
object FrameEncoder {
    fun encode(cmd: Int, payload: ByteArray = ByteArray(0), fsn: Int): ByteArray {
        val header = ByteArray(8)
        header[0] = Commands.SOF.toByte()
        writeLE16(header, 1, Commands.CTRL_HOST_CRC)
        writeLE16(header, 3, cmd)
        writeLE16(header, 5, payload.size)
        header[7] = fsn.toByte()

        val body = header + payload
        val crc = Crc16.compute(body)
        val frame = ByteArray(body.size + 2)
        body.copyInto(frame)
        frame[body.size] = (crc and 0xFF).toByte()
        frame[body.size + 1] = ((crc ushr 8) and 0xFF).toByte()
        return frame
    }

    private fun writeLE16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }
}

/**
 * Stateful incremental parser for the `0x55` frame stream. Feed it raw bytes
 * as they arrive off the RFCOMM socket; it buffers partial frames and emits
 * whatever complete frames it can decode, same approach as something-x's
 * `_process_buf`/`_process_x55`.
 *
 * Unknown lead bytes (not `0x55`) are dropped one at a time so a corrupted
 * or unexpected byte can't wedge the parser.
 */
class FrameParser {
    private var buffer: ByteArray = ByteArray(0)

    fun feed(chunk: ByteArray): List<Frame> {
        buffer += chunk
        val out = mutableListOf<Frame>()
        while (buffer.isNotEmpty()) {
            if (buffer[0].toInt() and 0xFF != Commands.SOF) {
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }
            if (buffer.size < 8) break

            val ctrl = readLE16(buffer, 1)
            val cmdRaw = readLE16(buffer, 3)
            val length = readLE16(buffer, 5)
            val crcSize = if (ctrl and 0x20 != 0) 2 else 0
            val total = 8 + length + crcSize
            if (buffer.size < total) break

            val payload = buffer.copyOfRange(8, 8 + length)
            // CRC mismatches are logged by callers if they care; we don't drop the
            // frame on mismatch, matching something-x's tolerant behavior.
            val cmd = Commands.normalizeRx(cmdRaw)
            out += Frame(cmd, payload)
            buffer = buffer.copyOfRange(total, buffer.size)
        }
        return out
    }

    private fun readLE16(buf: ByteArray, offset: Int): Int =
        (buf[offset].toInt() and 0xFF) or ((buf[offset + 1].toInt() and 0xFF) shl 8)
}
