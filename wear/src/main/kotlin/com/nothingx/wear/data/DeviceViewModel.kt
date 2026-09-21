package com.nothingx.wear.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nothingx.bluetooth.BondedDevice
import com.nothingx.bluetooth.BondedDevices
import com.nothingx.bluetooth.ConnectionState
import com.nothingx.protocol.AncMode
import com.nothingx.protocol.DeviceState
import com.nothingx.protocol.EqPreset
import com.nothingx.wear.connection.EarbudsConnectionHolder
import com.nothingx.wear.connection.EarbudsConnectionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Thin Compose-facing wrapper around [EarbudsConnectionHolder] — the actual
 * connection now lives at process scope (see its doc comment for why: so
 * the Tile can act on it too), not owned by this ViewModel. This class just
 * exposes it to Compose and starts/stops [EarbudsConnectionService] around
 * the device session.
 */
class DeviceViewModel(application: Application) : AndroidViewModel(application) {
    init {
        EarbudsConnectionHolder.init(application)
    }

    val connectionState: StateFlow<ConnectionState> = EarbudsConnectionHolder.connectionState
    val deviceState: StateFlow<DeviceState> = EarbudsConnectionHolder.deviceState

    private val prefs = DevicePrefs(application)

    private val _bondedDevices = MutableStateFlow<List<BondedDevice>>(emptyList())
    val bondedDevices: StateFlow<List<BondedDevice>> = _bondedDevices.asStateFlow()

    // The device the user tapped (or the last-connected one, resumed on app
    // open) — MainActivity watches this together with connectionState to
    // auto-navigate to the detail screen once it's actually Connected, not
    // the moment a tap starts a connection attempt. See MainActivity's
    // NothingXApp for the navigation effect this feeds.
    private val _pendingDevice = MutableStateFlow<BondedDevice?>(null)
    val pendingDevice: StateFlow<BondedDevice?> = _pendingDevice.asStateFlow()

    init {
        // Cache every state update so the Tile has a last-known snapshot to
        // show even on a cold read before EarbudsConnectionHolder is live
        // (e.g. tile shown right after a reboot, before anything reconnected).
        viewModelScope.launch {
            EarbudsConnectionHolder.deviceState.collect { prefs.cacheState(it) }
        }
        // Resume the last-connected device automatically on app open, instead
        // of requiring the user to re-tap it every time — this is what makes
        // opening the app feel like the earbuds are "just there" instead of
        // starting from a blank list every time, matching how a native
        // Bluetooth settings screen behaves.
        viewModelScope.launch {
            val address = prefs.lastDeviceAddress.first() ?: return@launch
            val name = prefs.lastKnownState.first().name ?: address
            connect(address, name)
        }
    }

    fun refreshBondedDevices() {
        _bondedDevices.value = BondedDevices.list(getApplication())
    }

    /** The phone's own bonded devices, for the phone-relay picker — see RelayDeviceListScreen. */
    val relayBondedDevices: StateFlow<List<BondedDevice>>
        get() = EarbudsConnectionHolder.relayBondedDevices

    fun queryRelayBondedDevices() = EarbudsConnectionHolder.queryRelayBondedDevices()

    fun connect(address: String, name: String) {
        _pendingDevice.value = BondedDevice(name = name, address = address, isSupported = true, isUnverified = false)
        EarbudsConnectionService.start(getApplication(), address)
        EarbudsConnectionHolder.connect(address)
        viewModelScope.launch { prefs.setLastDevice(address, name) }
    }

    /** Called once MainActivity has actually navigated to the detail screen for [pendingDevice]. */
    fun clearPendingDevice() {
        _pendingDevice.value = null
    }

    fun disconnect() {
        _pendingDevice.value = null
        EarbudsConnectionHolder.disconnect()
        EarbudsConnectionService.stop(getApplication())
    }

    fun setAncMode(mode: AncMode) = EarbudsConnectionHolder.setAncMode(mode)

    fun setEqPreset(preset: EqPreset) = EarbudsConnectionHolder.setEqPreset(preset)

    fun setInEarDetection(enabled: Boolean) = EarbudsConnectionHolder.setInEarDetection(enabled)

    fun setLowLatency(enabled: Boolean) = EarbudsConnectionHolder.setLowLatency(enabled)

    fun setPersonalizedAnc(enabled: Boolean) = EarbudsConnectionHolder.setPersonalizedAnc(enabled)

    fun setBassEnhance(enabled: Boolean, level: Int) = EarbudsConnectionHolder.setBassEnhance(enabled, level)

    fun ringBuds(ring: Boolean, isLeft: Boolean? = null) = EarbudsConnectionHolder.ringBuds(ring, isLeft)

    fun launchEarFitTest() = EarbudsConnectionHolder.launchEarFitTest()
}
