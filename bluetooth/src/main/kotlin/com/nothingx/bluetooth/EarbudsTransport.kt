package com.nothingx.bluetooth

import com.nothingx.protocol.AncMode
import com.nothingx.protocol.DeviceState
import com.nothingx.protocol.EqPreset
import kotlinx.coroutines.flow.StateFlow

/**
 * One transport for talking to a single earbuds device. [DirectRfcommTransport]
 * implements this by opening its own RFCOMM socket from the watch. A future
 * phone-relay transport (Data Layer relay to a phone-side executor, per the
 * plan discussed with the user) implements the same interface so the wear
 * UI/ViewModel layer never needs to know which one is active.
 */
interface EarbudsTransport {
    val connectionState: StateFlow<ConnectionState>
    val deviceState: StateFlow<DeviceState>

    /** Begins connecting to [address] (a Bluetooth MAC). Suspends until the attempt settles. */
    suspend fun connect(address: String)

    suspend fun disconnect()

    suspend fun setAncMode(mode: AncMode)

    suspend fun setEqPreset(preset: EqPreset)
}
