package com.nothingx.bluetooth.relay

import com.google.android.gms.wearable.DataMap
import com.nothingx.bluetooth.ConnectionState
import com.nothingx.protocol.AncMode
import com.nothingx.protocol.DeviceState
import com.nothingx.protocol.EarFitTestResult
import com.nothingx.protocol.EqPreset

/**
 * Wire format for the watch<->phone relay path, built on the Wearable Data
 * Layer API. Both [com.nothingx.wear] (WearRelayTransport) and
 * [com.nothingx.phone] (PhoneRelayService) depend on :bluetooth already,
 * so the shared codec lives here instead of being duplicated in each.
 *
 * Commands (watch -> phone) go over MessageClient at [RelayPaths]' CMD_*
 * paths, fire-and-forget — same no-ack semantics as
 * DirectRfcommTransport.sendCommand itself; the real protocol ack, if any,
 * comes back as a state push, not a Data Layer response.
 *
 * State (phone -> watch) goes over DataClient at the DATA_* paths.
 * DataClient (not more messages) deliberately: it holds a single "current
 * value" per path and only notifies listeners when that value actually
 * changes, so an idle relay connection costs nothing beyond the open
 * Bluetooth socket itself — no polling, no re-sending unchanged state.
 */
object RelayPaths {
    const val CMD_CONNECT = "/nothingx/cmd/connect"
    const val CMD_DISCONNECT = "/nothingx/cmd/disconnect"
    const val CMD_SET_ANC_MODE = "/nothingx/cmd/setAncMode"
    const val CMD_SET_EQ_PRESET = "/nothingx/cmd/setEqPreset"
    const val CMD_QUERY_SETTINGS = "/nothingx/cmd/querySettings"
    const val CMD_SET_IN_EAR_DETECTION = "/nothingx/cmd/setInEarDetection"
    const val CMD_SET_LOW_LATENCY = "/nothingx/cmd/setLowLatency"
    const val CMD_SET_PERSONALIZED_ANC = "/nothingx/cmd/setPersonalizedAnc"
    const val CMD_SET_BASS_ENHANCE = "/nothingx/cmd/setBassEnhance"
    const val CMD_RING_BUDS = "/nothingx/cmd/ringBuds"
    const val CMD_LAUNCH_EAR_FIT_TEST = "/nothingx/cmd/launchEarFitTest"

    const val DATA_DEVICE_STATE = "/nothingx/state/device"
    const val DATA_CONNECTION_STATE = "/nothingx/state/connection"
}

object RelayCodec {
    // ---- commands (MessageClient payloads) ----

    fun encodeConnect(address: String): ByteArray = address.toByteArray(Charsets.UTF_8)
    fun decodeConnect(payload: ByteArray): String = String(payload, Charsets.UTF_8)

    fun encodeBool(value: Boolean): ByteArray = byteArrayOf(if (value) 1 else 0)
    fun decodeBool(payload: ByteArray): Boolean = payload.isNotEmpty() && payload[0].toInt() == 1

    fun encodeAncMode(mode: AncMode): ByteArray = byteArrayOf(mode.ordinal.toByte())
    fun decodeAncMode(payload: ByteArray): AncMode =
        AncMode.entries.getOrElse(payload.getOrElse(0) { 0 }.toInt()) { AncMode.OFF }

    fun encodeEqPreset(preset: EqPreset): ByteArray = byteArrayOf(preset.ordinal.toByte())
    fun decodeEqPreset(payload: ByteArray): EqPreset =
        EqPreset.entries.getOrElse(payload.getOrElse(0) { 0 }.toInt()) { EqPreset.BALANCED }

    fun encodeBassEnhance(enabled: Boolean, level: Int): ByteArray =
        byteArrayOf(if (enabled) 1 else 0, level.coerceIn(0, 255).toByte())

    fun decodeBassEnhance(payload: ByteArray): Pair<Boolean, Int> =
        (payload.getOrElse(0) { 0 }.toInt() == 1) to (payload.getOrElse(1) { 0 }.toInt() and 0xFF)

    // ringBuds(ring, isLeft) — byte1: 0=null, 1=false, 2=true
    fun encodeRingBuds(ring: Boolean, isLeft: Boolean?): ByteArray =
        byteArrayOf(if (ring) 1 else 0, if (isLeft == null) 0 else if (isLeft) 2 else 1)

    fun decodeRingBuds(payload: ByteArray): Pair<Boolean, Boolean?> {
        val ring = payload.getOrElse(0) { 0 }.toInt() == 1
        val isLeft = when (payload.getOrElse(1) { 0 }.toInt()) {
            1 -> false
            2 -> true
            else -> null
        }
        return ring to isLeft
    }

    // ---- state (DataMap, phone -> watch) ----

    fun deviceStateToDataMap(state: DeviceState): DataMap = DataMap().apply {
        putInt("leftBattery", state.leftBattery)
        putInt("rightBattery", state.rightBattery)
        putInt("caseBattery", state.caseBattery)
        putInt("ancMode", state.ancMode.ordinal)
        putInt("eqPreset", state.eqPreset.ordinal)
        putBoolean("leftWearing", state.leftWearing)
        putBoolean("rightWearing", state.rightWearing)
        state.firmwareVersion?.let { putString("firmwareVersion", it) }
        state.serialNumber?.let { putString("serialNumber", it) }
        putBoolean("activated", state.activated)
        state.inEarDetectionEnabled?.let { putBoolean("inEarDetectionEnabled", it) }
        state.lowLatencyEnabled?.let { putBoolean("lowLatencyEnabled", it) }
        state.personalizedAncEnabled?.let { putBoolean("personalizedAncEnabled", it) }
        state.bassEnhanceEnabled?.let { putBoolean("bassEnhanceEnabled", it) }
        state.bassLevel?.let { putInt("bassLevel", it) }
        state.gestureCount?.let { putInt("gestureCount", it) }
        state.earFitTestResult?.let {
            putInt("earFitTestLeft", it.left)
            putInt("earFitTestRight", it.right)
        }
    }

    fun deviceStateFromDataMap(map: DataMap): DeviceState = DeviceState(
        leftBattery = map.getInt("leftBattery", -1),
        rightBattery = map.getInt("rightBattery", -1),
        caseBattery = map.getInt("caseBattery", -1),
        ancMode = AncMode.entries.getOrElse(map.getInt("ancMode", 0)) { AncMode.OFF },
        eqPreset = EqPreset.entries.getOrElse(map.getInt("eqPreset", 0)) { EqPreset.BALANCED },
        leftWearing = map.getBoolean("leftWearing", false),
        rightWearing = map.getBoolean("rightWearing", false),
        firmwareVersion = if (map.containsKey("firmwareVersion")) map.getString("firmwareVersion") else null,
        serialNumber = if (map.containsKey("serialNumber")) map.getString("serialNumber") else null,
        activated = map.getBoolean("activated", false),
        inEarDetectionEnabled = if (map.containsKey("inEarDetectionEnabled")) {
            map.getBoolean("inEarDetectionEnabled")
        } else {
            null
        },
        lowLatencyEnabled = if (map.containsKey("lowLatencyEnabled")) map.getBoolean("lowLatencyEnabled") else null,
        personalizedAncEnabled = if (map.containsKey("personalizedAncEnabled")) {
            map.getBoolean("personalizedAncEnabled")
        } else {
            null
        },
        bassEnhanceEnabled = if (map.containsKey("bassEnhanceEnabled")) map.getBoolean("bassEnhanceEnabled") else null,
        bassLevel = if (map.containsKey("bassLevel")) map.getInt("bassLevel") else null,
        gestureCount = if (map.containsKey("gestureCount")) map.getInt("gestureCount") else null,
        earFitTestResult = if (map.containsKey("earFitTestLeft") && map.containsKey("earFitTestRight")) {
            EarFitTestResult(map.getInt("earFitTestLeft"), map.getInt("earFitTestRight"))
        } else {
            null
        },
    )

    fun connectionStateToDataMap(state: ConnectionState): DataMap = DataMap().apply {
        when (state) {
            is ConnectionState.Idle -> putString("kind", "Idle")
            is ConnectionState.Connecting -> {
                putString("kind", "Connecting")
                putInt("channelsTried", state.channelsTried)
            }
            is ConnectionState.Connected -> putString("kind", "Connected")
            is ConnectionState.Disconnected -> putString("kind", "Disconnected")
            is ConnectionState.Failed -> {
                putString("kind", "Failed")
                putString("reason", state.reason)
            }
        }
    }

    fun connectionStateFromDataMap(map: DataMap): ConnectionState = when (map.getString("kind")) {
        "Connecting" -> ConnectionState.Connecting(map.getInt("channelsTried", 0))
        "Connected" -> ConnectionState.Connected
        "Disconnected" -> ConnectionState.Disconnected
        "Failed" -> ConnectionState.Failed(map.getString("reason") ?: "Unknown")
        else -> ConnectionState.Idle
    }
}
