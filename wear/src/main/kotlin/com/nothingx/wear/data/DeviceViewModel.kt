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

    val connectionState: StateFlow<ConnectionState> = EarbudsConnectionHolder.connectionState!!
    val deviceState: StateFlow<DeviceState> = EarbudsConnectionHolder.deviceState!!

    private val prefs = DevicePrefs(application)

    private val _bondedDevices = MutableStateFlow<List<BondedDevice>>(emptyList())
    val bondedDevices: StateFlow<List<BondedDevice>> = _bondedDevices.asStateFlow()

    init {
        // Cache every state update so the Tile has a last-known snapshot to
        // show even on a cold read before EarbudsConnectionHolder is live
        // (e.g. tile shown right after a reboot, before anything reconnected).
        viewModelScope.launch {
            EarbudsConnectionHolder.deviceState!!.collect { prefs.cacheState(it) }
        }
    }

    fun refreshBondedDevices() {
        _bondedDevices.value = BondedDevices.list(getApplication())
    }

    fun connect(address: String, name: String) {
        EarbudsConnectionService.start(getApplication(), address)
        EarbudsConnectionHolder.connect(address)
        viewModelScope.launch { prefs.setLastDevice(address, name) }
    }

    fun disconnect() {
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
