package com.nothingx.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameCodecTest {
    @Test
    fun `encode then parse round-trips cmd and payload`() {
        val payload = byteArrayOf(0x01, 0x05, 0x00)
        val frame = FrameEncoder.encode(Commands.SET_NOISE_REDUCTION, payload, fsn = 7)

        val parser = FrameParser()
        val decoded = parser.feed(frame)

        assertEquals(1, decoded.size)
        // response cmd would have bit15 cleared by the device; here we're just
        // checking the encoder produced a frame whose header round-trips as sent.
        assertEquals(Commands.normalizeRx(Commands.SET_NOISE_REDUCTION and 0x7FFF), decoded[0].cmd)
        assertTrue(decoded[0].payload.contentEquals(payload))
    }

    @Test
    fun `parser handles a frame split across multiple feeds`() {
        val frame = FrameEncoder.encode(Commands.GET_BATTERY, byteArrayOf(), fsn = 1)
        val parser = FrameParser()

        val firstHalf = frame.copyOfRange(0, 4)
        val secondHalf = frame.copyOfRange(4, frame.size)

        assertEquals(0, parser.feed(firstHalf).size)
        val decoded = parser.feed(secondHalf)
        assertEquals(1, decoded.size)
    }

    @Test
    fun `parser skips unknown lead bytes instead of getting stuck`() {
        val frame = FrameEncoder.encode(Commands.GET_BATTERY, byteArrayOf(), fsn = 1)
        val garbage = byteArrayOf(0x00, 0x01, 0x02) + frame

        val parser = FrameParser()
        val decoded = parser.feed(garbage)

        assertEquals(1, decoded.size)
    }

    @Test
    fun `parser decodes two frames back to back in one feed`() {
        val frameA = FrameEncoder.encode(Commands.GET_BATTERY, byteArrayOf(), fsn = 1)
        val frameB = FrameEncoder.encode(Commands.GET_NOISE_REDUCTION, byteArrayOf(0x03), fsn = 2)

        val parser = FrameParser()
        val decoded = parser.feed(frameA + frameB)

        assertEquals(2, decoded.size)
        assertEquals(Commands.normalizeRx(Commands.GET_BATTERY), decoded[0].cmd)
        assertEquals(Commands.normalizeRx(Commands.GET_NOISE_REDUCTION), decoded[1].cmd)
    }
}
