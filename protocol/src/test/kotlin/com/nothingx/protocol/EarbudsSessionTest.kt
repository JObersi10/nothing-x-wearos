package com.nothingx.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EarbudsSessionTest {
    @Test
    fun `activation handshake triggers SET_ACTIVATED then a full query burst`() {
        val session = EarbudsSession()

        val afterProbe = session.handleFrame(Frame(Commands.GET_PROTO_VERSION, ByteArray(0)))
        assertEquals(listOf(Commands.SET_ACTIVATED), afterProbe.map { it.cmd })
        assertFalse(session.state.activated)

        val afterAck = session.handleFrame(Frame(Commands.SET_ACTIVATED, ByteArray(0)))
        assertTrue(session.state.activated)
        assertEquals(
            listOf(
                Commands.GET_BATTERY,
                Commands.GET_NOISE_REDUCTION,
                Commands.GET_EARPHONE_STATUS,
                Commands.GET_HOST_VERSION,
                Commands.GET_REMOTE_CONF,
                Commands.GET_IN_EAR,
                Commands.GET_LATENCY,
                Commands.GET_PERSONALIZED_ANC,
                Commands.GET_ENHANCED_BASS,
                Commands.GET_GESTURE,
            ),
            afterAck.map { it.cmd },
        )
    }

    @Test
    fun `battery payload updates left, right and case`() {
        val session = EarbudsSession()
        // count=3, then (type=2,val=80) left, (type=3,val=45) right, (type=4,val=90) case
        val payload = byteArrayOf(3, 2, 80, 3, 45, 4, 90)

        session.handleFrame(Frame(Commands.GET_BATTERY, payload))

        assertEquals(80, session.state.leftBattery)
        assertEquals(45, session.state.rightBattery)
        assertEquals(90, session.state.caseBattery)
    }

    @Test
    fun `charging bit is masked out of the reported percentage`() {
        val session = EarbudsSession()
        // type=2, val=0x80 or 60 = charging + 60%
        val payload = byteArrayOf(1, 2, (0x80 or 60).toByte())

        session.handleFrame(Frame(Commands.GET_BATTERY, payload))

        assertEquals(60, session.state.leftBattery)
    }

    @Test
    fun `stereo battery mirrors onto both left and right`() {
        val session = EarbudsSession()
        val payload = byteArrayOf(1, Commands.BATTERY_STEREO.toByte(), 77)

        session.handleFrame(Frame(Commands.GET_BATTERY, payload))

        assertEquals(77, session.state.leftBattery)
        assertEquals(77, session.state.rightBattery)
    }

    @Test
    fun `anc mode entry updates state to transparency`() {
        val session = EarbudsSession()
        val payload = byteArrayOf(1, Commands.ANC_WIRE_TRANSPARENCY.toByte(), 0)

        session.handleFrame(Frame(Commands.GET_NOISE_REDUCTION, payload))

        assertEquals(AncMode.TRANSPARENCY, session.state.ancMode)
    }

    @Test
    fun `setAncMode updates local state and encodes the correct wire command`() {
        val session = EarbudsSession()

        val cmd = session.setAncMode(AncMode.NOISE_CANCELLATION)

        assertEquals(AncMode.NOISE_CANCELLATION, session.state.ancMode)
        assertEquals(Commands.SET_NOISE_REDUCTION, cmd.cmd)
        assertTrue(cmd.payload.contentEquals(byteArrayOf(0x01, Commands.ANC_WIRE_STRONG.toByte(), 0x00)))
    }

    @Test
    fun `setEqPreset updates local state and encodes the wire value`() {
        val session = EarbudsSession()

        val cmd = session.setEqPreset(EqPreset.MORE_BASS)

        assertEquals(EqPreset.MORE_BASS, session.state.eqPreset)
        assertTrue(cmd.payload.contentEquals(byteArrayOf(1)))
    }

    @Test
    fun `earphone status updates wearing state per side`() {
        val session = EarbudsSession()
        // count=2: left worn (bit2 set), right not worn
        val payload = byteArrayOf(2, 2, 0x04, 3, 0x00)

        session.handleFrame(Frame(Commands.GET_EARPHONE_STATUS, payload))

        assertTrue(session.state.leftWearing)
        assertFalse(session.state.rightWearing)
    }

    @Test
    fun `EVT_STATUS push triggers a re-query instead of trusting the stale payload`() {
        val session = EarbudsSession()

        val commands = session.handleFrame(Frame(Commands.EVT_STATUS, byteArrayOf(0x00)))

        assertEquals(listOf(Commands.GET_EARPHONE_STATUS), commands.map { it.cmd })
    }

    @Test
    fun `serial number is extracted from the field-4 CSV line`() {
        val session = EarbudsSession()
        val raw = "1,1,foo\n1,4,SH10212543006451\n1,7,bar"
        val payload = raw.toByteArray(Charsets.UTF_8)

        session.handleFrame(Frame(Commands.GET_REMOTE_CONF, payload))

        assertEquals("SH10212543006451", session.state.serialNumber)
    }

    @Test
    fun `firmware version string is trimmed of trailing nulls`() {
        val session = EarbudsSession()
        val payload = "1.2.3\u0000\u0000".toByteArray(Charsets.UTF_8)

        session.handleFrame(Frame(Commands.GET_HOST_VERSION, payload))

        assertEquals("1.2.3", session.state.firmwareVersion)
    }

    @Test
    fun `in-ear detection state is read from payload index 2`() {
        val session = EarbudsSession()

        session.handleFrame(Frame(Commands.GET_IN_EAR, byteArrayOf(0, 0, 1)))

        assertEquals(true, session.state.inEarDetectionEnabled)
    }

    @Test
    fun `low latency 1 means on, 2 means off`() {
        val session = EarbudsSession()

        session.handleFrame(Frame(Commands.GET_LATENCY, byteArrayOf(1)))
        assertEquals(true, session.state.lowLatencyEnabled)

        session.handleFrame(Frame(Commands.GET_LATENCY, byteArrayOf(2)))
        assertEquals(false, session.state.lowLatencyEnabled)
    }

    @Test
    fun `bass enhance level is halved from the doubled wire value`() {
        val session = EarbudsSession()

        session.handleFrame(Frame(Commands.GET_ENHANCED_BASS, byteArrayOf(1, 6)))

        assertEquals(true, session.state.bassEnhanceEnabled)
        assertEquals(3, session.state.bassLevel)
    }

    @Test
    fun `setBassEnhance doubles the level for the wire payload`() {
        val session = EarbudsSession()

        val cmd = session.setBassEnhance(enabled = true, level = 3)

        assertEquals(Commands.SET_ENHANCED_BASS, cmd.cmd)
        assertTrue(cmd.payload.contentEquals(byteArrayOf(0x01, 6)))
    }

    @Test
    fun `ringBuds without a side rings both, Ear (1) style`() {
        val session = EarbudsSession()

        val cmd = session.ringBuds(ring = true)

        assertEquals(Commands.RING_BUDS, cmd.cmd)
        assertTrue(cmd.payload.contentEquals(byteArrayOf(0x01)))
    }

    @Test
    fun `ringBuds with a side encodes device byte then ring flag`() {
        val session = EarbudsSession()

        val cmd = session.ringBuds(ring = true, isLeft = false)

        assertTrue(cmd.payload.contentEquals(byteArrayOf(0x03, 0x01)))
    }

    @Test
    fun `activation also fires the settings query burst`() {
        val session = EarbudsSession()
        session.handleFrame(Frame(Commands.GET_PROTO_VERSION, ByteArray(0)))

        val afterAck = session.handleFrame(Frame(Commands.SET_ACTIVATED, ByteArray(0)))

        assertTrue(afterAck.map { it.cmd }.containsAll(session.querySettingsCommand().map { it.cmd }))
    }
}
