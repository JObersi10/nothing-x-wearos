package com.nothingx.protocol

/**
 * Pure, transport-agnostic protocol state machine for one connected Nothing
 * Ear device. Feed it decoded [Frame]s as they arrive off the RFCOMM socket;
 * it updates [state] and tells the caller what to send back, mirroring
 * something-x's `NothingDevice._dispatch_x55` but without any I/O, threading,
 * or GTK/GLib dependency, so both the wear direct-transport and the phone
 * relay executor can share one implementation and it's unit-testable on the
 * plain JVM.
 *
 * Not thread-safe — callers own their own serialization (e.g. a single
 * coroutine reading the socket).
 */
class EarbudsSession {
    var state: DeviceState = DeviceState()
        private set

    /**
     * Call once the RFCOMM channel is open and the probe's response frame has
     * been parsed. Kicks off activation.
     */
    fun handleFrame(frame: Frame): List<OutgoingCommand> {
        val cmds = mutableListOf<OutgoingCommand>()
        when (frame.cmd) {
            Commands.GET_PROTO_VERSION -> {
                cmds += OutgoingCommand(Commands.SET_ACTIVATED, label = "activate")
            }
            Commands.SET_ACTIVATED -> {
                state = state.copy(activated = true)
                cmds += queryAllCommand()
            }
            Commands.GET_BATTERY, Commands.EVT_BATTERY -> applyBattery(frame.payload)
            Commands.GET_NOISE_REDUCTION, Commands.EVT_NOISE_REDUCTION -> applyAnc(frame.payload)
            Commands.GET_EARPHONE_STATUS -> applyEarphoneStatus(frame.payload)
            Commands.EVT_STATUS -> {
                // Pushed event only carries fresh data for the bud that changed;
                // re-query for a full accurate snapshot, same as something-x does.
                cmds += OutgoingCommand(Commands.GET_EARPHONE_STATUS, label = "re-query earphone status")
            }
            Commands.GET_HOST_VERSION -> {
                val version = decodeTrimmedString(frame.payload)
                if (!version.isNullOrEmpty()) state = state.copy(firmwareVersion = version)
            }
            Commands.GET_REMOTE_CONF -> {
                val serial = parseSerialNumber(frame.payload)
                if (serial != null) state = state.copy(serialNumber = serial)
            }
        }
        return cmds
    }

    /** GET_BATTERY + GET_NOISE_REDUCTION + GET_EARPHONE_STATUS + GET_HOST_VERSION + GET_REMOTE_CONF. */
    fun queryAllCommand(): List<OutgoingCommand> = listOf(
        OutgoingCommand(Commands.GET_BATTERY, label = "get battery"),
        OutgoingCommand(Commands.GET_NOISE_REDUCTION, byteArrayOf(0x03), label = "get anc"),
        OutgoingCommand(Commands.GET_EARPHONE_STATUS, label = "get earphone status"),
        OutgoingCommand(Commands.GET_HOST_VERSION, label = "get firmware"),
        OutgoingCommand(Commands.GET_REMOTE_CONF, label = "get serial"),
    )

    fun setAncMode(mode: AncMode): OutgoingCommand {
        state = state.copy(ancMode = mode)
        return OutgoingCommand(
            Commands.SET_NOISE_REDUCTION,
            byteArrayOf(0x01, mode.wireValue.toByte(), 0x00),
            label = "set anc=$mode",
        )
    }

    fun setEqPreset(preset: EqPreset): OutgoingCommand {
        state = state.copy(eqPreset = preset)
        return OutgoingCommand(Commands.SET_EQ, byteArrayOf(preset.wireValue.toByte()), label = "set eq=$preset")
    }

    private fun applyBattery(payload: ByteArray) {
        val parsed = Payloads.parseBattery(payload)
        var s = state
        parsed[Commands.BATTERY_STEREO]?.let { s = s.copy(leftBattery = it, rightBattery = it) }
        parsed[Commands.BATTERY_LEFT]?.let { s = s.copy(leftBattery = it) }
        parsed[Commands.BATTERY_RIGHT]?.let { s = s.copy(rightBattery = it) }
        parsed[Commands.BATTERY_CASE]?.let { s = s.copy(caseBattery = it) }
        state = s
    }

    private fun applyAnc(payload: ByteArray) {
        val snapshot = Payloads.parseAnc(payload) ?: return
        snapshot.mode?.let { state = state.copy(ancMode = it) }
    }

    private fun applyEarphoneStatus(payload: ByteArray) {
        val parsed = Payloads.parseEarphoneStatus(payload)
        var s = state
        parsed[Commands.BATTERY_STEREO]?.let { s = s.copy(leftWearing = it, rightWearing = it) }
        parsed[2]?.let { s = s.copy(leftWearing = it) }
        parsed[3]?.let { s = s.copy(rightWearing = it) }
        state = s
    }

    private fun decodeTrimmedString(payload: ByteArray): String? =
        payload.toString(Charsets.UTF_8).trim('\u0000', ' ').ifEmpty { null }

    /** Payload: newline-separated "device_id,field_id,value" entries; field 4 = serial. */
    private fun parseSerialNumber(payload: ByteArray): String? {
        val raw = payload.toString(Charsets.UTF_8).trim('\u0000')
        for (line in raw.lineSequence()) {
            val parts = line.split(",", limit = 3)
            if (parts.size == 3 && parts[1] == "4" && parts[2].isNotBlank()) {
                return parts[2].trim()
            }
        }
        return null
    }
}
