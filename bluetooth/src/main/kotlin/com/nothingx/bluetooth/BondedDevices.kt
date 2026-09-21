package com.nothingx.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

data class BondedDevice(val name: String, val address: String, val isSupported: Boolean, val isUnverified: Boolean)

/** Lists paired Classic Bluetooth devices — the watch's own bonded set, same as something-x's home screen. */
object BondedDevices {
    @SuppressLint("MissingPermission")
    fun list(context: Context): List<BondedDevice> {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return emptyList()

        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()

        return adapter.bondedDevices.orEmpty().map { device ->
            val name = device.name ?: device.address
            BondedDevice(
                name = name,
                address = device.address,
                isSupported = NothingDeviceMatcher.isSupportedEarbuds(name),
                isUnverified = NothingDeviceMatcher.isUnverifiedCmf(name),
            )
        }.sortedWith(compareByDescending<BondedDevice> { it.isSupported }.thenBy { it.name })
    }
}
