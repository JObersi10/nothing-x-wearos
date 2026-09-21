package com.nothingx.wear.connection

import android.content.Context
import com.nothingx.bluetooth.ConnectionState
import com.nothingx.bluetooth.DirectRfcommTransport
import com.nothingx.bluetooth.EarbudsTransport
import com.nothingx.protocol.AncMode
import com.nothingx.protocol.DeviceState
import com.nothingx.protocol.EqPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
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
 */
object EarbudsConnectionHolder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var transport: EarbudsTransport? = null
    private var appContext: Context? = null

    val connectionState: StateFlow<ConnectionState>?
        get() = transport?.connectionState

    val deviceState: StateFlow<DeviceState>?
        get() = transport?.deviceState

    fun init(context: Context) {
        if (transport != null) return
        appContext = context.applicationContext
        transport = DirectRfcommTransport(context.applicationContext)
    }

    private var connectedAddress: String? = null

    fun connect(address: String) {
        if (connectedAddress == address) return
        connectedAddress = address
        val ctx = appContext ?: return
        init(ctx)
        scope.launch { transport?.connect(address) }
    }

    fun disconnect() {
        connectedAddress = null
        scope.launch { transport?.disconnect() }
    }

    fun setAncMode(mode: AncMode) {
        scope.launch { transport?.setAncMode(mode) }
    }

    fun setEqPreset(preset: EqPreset) {
        scope.launch { transport?.setEqPreset(preset) }
    }

    fun setInEarDetection(enabled: Boolean) {
        scope.launch { transport?.setInEarDetection(enabled) }
    }

    fun setLowLatency(enabled: Boolean) {
        scope.launch { transport?.setLowLatency(enabled) }
    }

    fun setPersonalizedAnc(enabled: Boolean) {
        scope.launch { transport?.setPersonalizedAnc(enabled) }
    }

    fun setBassEnhance(enabled: Boolean, level: Int) {
        scope.launch { transport?.setBassEnhance(enabled, level) }
    }

    fun ringBuds(ring: Boolean, isLeft: Boolean? = null) {
        scope.launch { transport?.ringBuds(ring, isLeft) }
    }

    fun launchEarFitTest() {
        scope.launch { transport?.launchEarFitTest() }
    }
}
