package com.nothingx.wear.connection

import android.content.Context
import com.nothingx.bluetooth.BondedDevice
import com.nothingx.bluetooth.ConnectionState
import com.nothingx.bluetooth.DirectRfcommTransport
import com.nothingx.bluetooth.EarbudsTransport
import com.nothingx.protocol.AncMode
import com.nothingx.protocol.DeviceState
import com.nothingx.protocol.EqPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-wide singleton owning the one live [EarbudsTransport] connection,
 * so it survives an individual Activity's lifecycle. Both `MainActivity`
 * (via [com.nothingx.wear.data.DeviceViewModel]) and `NothingXTileService`
 * read/act on this same instance — they're the same app process on Wear OS
 * (no separate `android:process` declared), so a plain singleton is enough;
 * no cross-process IPC needed.
 *
 * This is what makes "control ANC from the Tile without opening the app"
 * possible: a Tile action (via `TileActionActivity`, see its doc comment for
 * why that's an invisible trampoline rather than a native Tile state
 * round-trip) calls straight into this same connection instead of a
 * process-local one that would already be gone by the time the Tile is
 * tapped.
 *
 * Holds both a [DirectRfcommTransport] and a [WearRelayTransport], lazily,
 * and exposes ONE stable pair of [connectionState]/[deviceState] flows that
 * always mirror whichever is currently active — this is what makes picking
 * relay vs. direct a normal per-connection choice (tap a different entry in
 * the device list) instead of a restart-required global setting the way an
 * earlier version of this required. [DeviceViewModel] captures these flows
 * once, non-null, at construction; [activate] is how the active transport
 * changes without leaving that reference stale — it re-points an internal
 * forwarding job at the new transport's flows rather than swapping the
 * flow identity itself.
 */
object EarbudsConnectionHolder {
    /**
     * Sentinel "address" for the phone relay entry in the device list — see
     * DeviceListScreen. Tapping it navigates to `RelayDeviceListScreen`
     * rather than connecting directly (MainActivity intercepts it before
     * calling [connect]); it's never passed to [connect] itself anymore.
     */
    const val RELAY_TARGET_ADDRESS = "relay"

    /**
     * Prefix for a relay address that names a specific phone-bonded device,
     * e.g. `"relay:AA:BB:CC:DD:EE:FF"` — what `RelayDeviceListScreen` passes
     * to [connect] once the user picks a device from the phone's own bonded
     * list (queried via [queryRelayBondedDevices]), instead of the old blind
     * "just pick whatever's matched" auto-connect, which wasn't resolving
     * reliably and forced opening the phone app to pick manually there.
     */
    const val RELAY_ADDRESS_PREFIX = "relay:"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appContext: Context? = null

    private var directTransport: DirectRfcommTransport? = null
    private var relayTransport: WearRelayTransport? = null
    private var activeTransport: EarbudsTransport? = null
    private var forwardJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _deviceState = MutableStateFlow(DeviceState())
    val deviceState: StateFlow<DeviceState> = _deviceState.asStateFlow()

    private var connectedAddress: String? = null

    // Bounded, backed-off reconnect for the direct transport only — the relay
    // path's reconnect is the phone's job (PhoneRelayService owns its own
    // DirectRfcommTransport and gets this same fix there). Bounded on
    // purpose: an earlier version had no reconnect logic at all, which left
    // a stale "Connected" state forever once the earbuds actually dropped;
    // an *unbounded* retry loop would trade that bug for a new one (endless
    // background connection attempts every time the earbuds are simply out
    // of range, e.g. left at home). Stopping after a few tries and requiring
    // an explicit retry (reopen the app, tap the device again) is the
    // middle ground.
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val reconnectDelaysMs = longArrayOf(5_000, 15_000, 30_000, 60_000)

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
    }

    private fun directTransport(): DirectRfcommTransport {
        val ctx = appContext ?: error("EarbudsConnectionHolder.init() not called yet")
        return directTransport ?: DirectRfcommTransport(ctx).also { directTransport = it }
    }

    private fun relayTransport(): WearRelayTransport {
        val ctx = appContext ?: error("EarbudsConnectionHolder.init() not called yet")
        return relayTransport ?: WearRelayTransport(ctx).also { relayTransport = it }
    }

    private fun activate(transport: EarbudsTransport) {
        if (activeTransport === transport) return
        activeTransport = transport
        forwardJob?.cancel()
        forwardJob = scope.launch {
            launch { transport.connectionState.collect { _connectionState.value = it } }
            launch { transport.deviceState.collect { _deviceState.value = it } }
        }
    }

    /** [address] may be a real Bluetooth MAC (direct connect) or [RELAY_TARGET_ADDRESS]. */
    fun connect(address: String) {
        // Checked before touching reconnectJob: a redundant connect() call to
        // the address we're already on (e.g. DeviceDetailScreen's own
        // connect() safety-net firing after the list screen already started
        // one) must be a true no-op — cancelling the reconnect watcher here
        // unconditionally would silently kill it on every such call.
        if (connectedAddress == address && activeTransport != null) return
        reconnectJob?.cancel()
        connectedAddress = address
        reconnectAttempts = 0
        val ctx = appContext ?: return
        init(ctx)
        if (address == RELAY_TARGET_ADDRESS || address.startsWith(RELAY_ADDRESS_PREFIX)) {
            val t = relayTransport()
            activate(t)
            // A bare RELAY_TARGET_ADDRESS (blank phone-side address) means
            // "whatever matched device is already paired to the phone" — kept
            // as a fallback for callers that still pass it (e.g. resuming a
            // last-connected device saved before this prefix existed), but
            // the watch UI no longer connects this way directly; see
            // RELAY_ADDRESS_PREFIX's doc comment.
            val phoneAddress = address.removePrefix(RELAY_ADDRESS_PREFIX).let { if (it == RELAY_TARGET_ADDRESS) "" else it }
            scope.launch { t.connect(phoneAddress) }
        } else {
            val t = directTransport()
            activate(t)
            scope.launch { t.connect(address) }
            observeForReconnect(t, address)
        }
    }

    /** Triggers a fresh phone bonded-device lookup; results land in [relayBondedDevices]. */
    fun queryRelayBondedDevices() {
        val ctx = appContext ?: return
        init(ctx)
        relayTransport().queryBondedDevices()
    }

    /** The phone's own bonded devices, as of the last [queryRelayBondedDevices] response. */
    val relayBondedDevices: StateFlow<List<BondedDevice>>
        get() = relayTransport().bondedDevices

    private fun observeForReconnect(transport: DirectRfcommTransport, address: String) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            transport.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> reconnectAttempts = 0
                    is ConnectionState.Disconnected, is ConnectionState.Failed -> {
                        // Only retry if we still actually want this device connected
                        // (not an explicit user disconnect() or a switch to a
                        // different target, both of which change connectedAddress).
                        if (connectedAddress != address) return@collect
                        if (reconnectAttempts >= reconnectDelaysMs.size) return@collect
                        val delayMs = reconnectDelaysMs[reconnectAttempts]
                        reconnectAttempts++
                        delay(delayMs)
                        if (connectedAddress == address) transport.connect(address)
                    }
                    else -> {}
                }
            }
        }
    }

    fun disconnect() {
        connectedAddress = null
        reconnectJob?.cancel()
        reconnectAttempts = 0
        val t = activeTransport
        scope.launch { t?.disconnect() }
    }

    fun setAncMode(mode: AncMode) {
        scope.launch { activeTransport?.setAncMode(mode) }
    }

    fun setEqPreset(preset: EqPreset) {
        scope.launch { activeTransport?.setEqPreset(preset) }
    }

    fun setInEarDetection(enabled: Boolean) {
        scope.launch { activeTransport?.setInEarDetection(enabled) }
    }

    fun setLowLatency(enabled: Boolean) {
        scope.launch { activeTransport?.setLowLatency(enabled) }
    }

    fun setPersonalizedAnc(enabled: Boolean) {
        scope.launch { activeTransport?.setPersonalizedAnc(enabled) }
    }

    fun setBassEnhance(enabled: Boolean, level: Int) {
        scope.launch { activeTransport?.setBassEnhance(enabled, level) }
    }

    fun ringBuds(ring: Boolean, isLeft: Boolean? = null) {
        scope.launch { activeTransport?.ringBuds(ring, isLeft) }
    }

    fun launchEarFitTest() {
        scope.launch { activeTransport?.launchEarFitTest() }
    }
}
