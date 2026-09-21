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

    /** Fetches in-ear detection, low latency, personalized ANC, bass enhance, gesture count. */
    suspend fun querySettings()

    suspend fun setInEarDetection(enabled: Boolean)

    suspend fun setLowLatency(enabled: Boolean)

    suspend fun setPersonalizedAnc(enabled: Boolean)

    suspend fun setBassEnhance(enabled: Boolean, level: Int)

    suspend fun ringBuds(ring: Boolean, isLeft: Boolean? = null)

    suspend fun launchEarFitTest()
}
