package com.nothingx.wear.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nothingx.bluetooth.BondedDevice
import com.nothingx.bluetooth.BondedDevices
import com.nothingx.bluetooth.ConnectionState
import com.nothingx.bluetooth.DirectRfcommTransport
import com.nothingx.bluetooth.EarbudsTransport
import com.nothingx.protocol.AncMode
import com.nothingx.protocol.DeviceState
import com.nothingx.protocol.EqPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceViewModel(application: Application) : AndroidViewModel(application) {
    // Direct transport is the primary path per the plan; a phone-relay
    // EarbudsTransport implementation can be swapped in here later as a
    // fallback without touching the UI layer.
    private val transport: EarbudsTransport = DirectRfcommTransport(application)
    private val prefs = DevicePrefs(application)

    val connectionState: StateFlow<ConnectionState> = transport.connectionState
    val deviceState: StateFlow<DeviceState> = transport.deviceState

    private val _bondedDevices = MutableStateFlow<List<BondedDevice>>(emptyList())
    val bondedDevices: StateFlow<List<BondedDevice>> = _bondedDevices.asStateFlow()

    init {
        // Cache every state update so the Tile (which can't hold a live RFCOMM
        // connection) has a last-known snapshot to show. See NothingXTileService.
        viewModelScope.launch {
            transport.deviceState.collect { prefs.cacheState(it) }
        }
    }

    fun refreshBondedDevices() {
        _bondedDevices.value = BondedDevices.list(getApplication())
    }

    fun connect(address: String, name: String) {
        viewModelScope.launch {
            transport.connect(address)
            prefs.setLastDevice(address, name)
        }
    }

    fun disconnect() {
        viewModelScope.launch { transport.disconnect() }
    }

    fun setAncMode(mode: AncMode) {
        viewModelScope.launch { transport.setAncMode(mode) }
    }

    fun setEqPreset(preset: EqPreset) {
        viewModelScope.launch { transport.setEqPreset(preset) }
    }
}
