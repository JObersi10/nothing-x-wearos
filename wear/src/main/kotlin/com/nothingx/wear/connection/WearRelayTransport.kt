package com.nothingx.wear.connection

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.nothingx.bluetooth.BondedDevice
import com.nothingx.bluetooth.ConnectionState
import com.nothingx.bluetooth.EarbudsTransport
import com.nothingx.bluetooth.relay.RelayCodec
import com.nothingx.bluetooth.relay.RelayPaths
import com.nothingx.protocol.AncMode
import com.nothingx.protocol.DeviceState
import com.nothingx.protocol.EqPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "NothingXRelay"

/**
 * Watch-side transport that relays commands to a phone-side
 * `PhoneRelayService` instead of opening its own RFCOMM socket — the
 * fallback path for watch/earbuds pairs where direct connect doesn't work.
 * Implements the same [EarbudsTransport] interface as
 * `DirectRfcommTransport` so nothing above it (`EarbudsConnectionHolder`,
 * the UI, the Tile) needs to know which transport is actually active.
 *
 * See `RelayProtocol.kt` in :bluetooth for why commands use MessageClient
 * (fire-and-forget) and state uses DataClient (only fires on an actual
 * change, so an idle relay costs nothing).
 */
class WearRelayTransport(context: Context) : EarbudsTransport {
    private val appContext = context.applicationContext
    private val messageClient = Wearable.getMessageClient(appContext)
    private val dataClient: DataClient = Wearable.getDataClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _deviceState = MutableStateFlow(DeviceState())
    override val deviceState: StateFlow<DeviceState> = _deviceState.asStateFlow()

    /** The phone's own bonded devices, as of the last [queryBondedDevices] response. */
    private val _bondedDevices = MutableStateFlow<List<BondedDevice>>(emptyList())
    val bondedDevices: StateFlow<List<BondedDevice>> = _bondedDevices.asStateFlow()

    private val dataListener = DataClient.OnDataChangedListener { events ->
        Log.d(TAG, "dataListener: ${events.count} event(s)")
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            when (event.dataItem.uri.path) {
                RelayPaths.DATA_DEVICE_STATE -> {
                    Log.d(TAG, "received DATA_DEVICE_STATE")
                    _deviceState.value = RelayCodec.deviceStateFromDataMap(map)
                }
                RelayPaths.DATA_CONNECTION_STATE -> {
                    Log.d(TAG, "received DATA_CONNECTION_STATE: ${map.getString("kind")}")
                    _connectionState.value = RelayCodec.connectionStateFromDataMap(map)
                }
                RelayPaths.DATA_BONDED_DEVICES -> {
                    val devices = RelayCodec.bondedDevicesFromDataMap(map)
                    Log.i(TAG, "received DATA_BONDED_DEVICES: ${devices.size} device(s): " + devices.joinToString { it.name })
                    _bondedDevices.value = devices
                }
                else -> Log.d(TAG, "dataListener: unhandled path ${event.dataItem.uri.path}")
            }
        }
        events.release()
    }

    init {
        dataClient.addListener(dataListener)
    }

    /** Stops listening for phone state pushes. Does not disconnect the phone's own RFCOMM link. */
    fun close() {
        dataClient.removeListener(dataListener)
    }

    private fun send(path: String, payload: ByteArray = ByteArray(0)) {
        Log.i(TAG, "send $path: looking up connected nodes…")
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Log.w(TAG, "send $path: no connected nodes (phone unreachable — is the phone app's Bluetooth/Wi-Fi Data Layer link up?)")
                    return@addOnSuccessListener
                }
                Log.i(TAG, "send $path: ${nodes.size} connected node(s): " + nodes.joinToString { it.displayName })
                for (node in nodes) {
                    messageClient.sendMessage(node.id, path, payload)
                        .addOnSuccessListener { Log.i(TAG, "send $path to ${node.displayName}: delivered to Play Services") }
                        .addOnFailureListener { e -> Log.w(TAG, "send $path to ${node.id} failed: ${e.message}") }
                }
            }
            .addOnFailureListener { e -> Log.w(TAG, "connectedNodes lookup failed: ${e.message}") }
    }

    override suspend fun connect(address: String) {
        Log.i(TAG, "connect($address) via phone relay")
        send(RelayPaths.CMD_CONNECT, RelayCodec.encodeConnect(address))
    }

    /** Asks the phone for its bonded-device list; response lands in [bondedDevices]. */
    fun queryBondedDevices() {
        Log.i(TAG, "queryBondedDevices() via phone relay")
        send(RelayPaths.CMD_QUERY_BONDED_DEVICES)
    }

    override suspend fun disconnect() {
        send(RelayPaths.CMD_DISCONNECT)
    }

    override suspend fun setAncMode(mode: AncMode) {
        send(RelayPaths.CMD_SET_ANC_MODE, RelayCodec.encodeAncMode(mode))
    }

    override suspend fun setEqPreset(preset: EqPreset) {
        send(RelayPaths.CMD_SET_EQ_PRESET, RelayCodec.encodeEqPreset(preset))
    }

    override suspend fun querySettings() {
        send(RelayPaths.CMD_QUERY_SETTINGS)
    }

    override suspend fun setInEarDetection(enabled: Boolean) {
        send(RelayPaths.CMD_SET_IN_EAR_DETECTION, RelayCodec.encodeBool(enabled))
    }

    override suspend fun setLowLatency(enabled: Boolean) {
        send(RelayPaths.CMD_SET_LOW_LATENCY, RelayCodec.encodeBool(enabled))
    }

    override suspend fun setPersonalizedAnc(enabled: Boolean) {
        send(RelayPaths.CMD_SET_PERSONALIZED_ANC, RelayCodec.encodeBool(enabled))
    }

    override suspend fun setBassEnhance(enabled: Boolean, level: Int) {
        send(RelayPaths.CMD_SET_BASS_ENHANCE, RelayCodec.encodeBassEnhance(enabled, level))
    }

    override suspend fun ringBuds(ring: Boolean, isLeft: Boolean?) {
        send(RelayPaths.CMD_RING_BUDS, RelayCodec.encodeRingBuds(ring, isLeft))
    }

    override suspend fun launchEarFitTest() {
        send(RelayPaths.CMD_LAUNCH_EAR_FIT_TEST)
    }
}
