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

    // Tracks which device this session is connected to, so connect() calls
    // from screens that re-enter composition (e.g. navigating back from
    // Settings to the detail screen) don't tear down and restart a perfectly
    // good connection — that was the actual bug behind "find my earbuds/ear
    // fit test/low lag mode don't work": the connection was being closed the
    // moment the user left the detail screen for Settings, before this fix.
    private var connectedAddress: String? = null

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
        if (connectedAddress == address) return
        connectedAddress = address
        viewModelScope.launch {
            transport.connect(address)
            prefs.setLastDevice(address, name)
        }
    }

    fun disconnect() {
        connectedAddress = null
        viewModelScope.launch { transport.disconnect() }
    }

    fun setAncMode(mode: AncMode) {
        viewModelScope.launch { transport.setAncMode(mode) }
    }

    fun setEqPreset(preset: EqPreset) {
        viewModelScope.launch { transport.setEqPreset(preset) }
    }

    fun setInEarDetection(enabled: Boolean) {
        viewModelScope.launch { transport.setInEarDetection(enabled) }
    }

    fun setLowLatency(enabled: Boolean) {
        viewModelScope.launch { transport.setLowLatency(enabled) }
    }

    fun setPersonalizedAnc(enabled: Boolean) {
        viewModelScope.launch { transport.setPersonalizedAnc(enabled) }
    }

    fun setBassEnhance(enabled: Boolean, level: Int) {
        viewModelScope.launch { transport.setBassEnhance(enabled, level) }
    }

    fun ringBuds(ring: Boolean, isLeft: Boolean? = null) {
        viewModelScope.launch { transport.ringBuds(ring, isLeft) }
    }

    fun launchEarFitTest() {
        viewModelScope.launch { transport.launchEarFitTest() }
    }

    // No onCleared() cleanup here on purpose: viewModelScope is already
    // cancelled by the time onCleared() runs (Android cancels it before
    // invoking onCleared()), so a viewModelScope.launch { transport.disconnect() }
    // there would silently do nothing — tried it, reverted it rather than ship
    // a comment claiming cleanup that doesn't actually happen. The RFCOMM
    // socket leaking if the user exits without passing back through
    // DeviceListScreen (which is where disconnect() actually fires) is a real,
    // known gap — see HANDOFF.md.
}
