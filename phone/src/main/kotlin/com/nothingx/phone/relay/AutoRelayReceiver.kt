package com.nothingx.phone.relay

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.nothingx.bluetooth.NothingDeviceMatcher
import com.nothingx.bluetooth.log.NothingXLog as Log

private const val TAG = "NothingXRelay"

/**
 * Starts [PhoneRelayService] automatically the moment the phone's own
 * Bluetooth connects to a matched Nothing/CMF device — this is what removes
 * the "open the phone app and tap a device" step the first version of this
 * relay required. `ACTION_ACL_CONNECTED`/`ACTION_ACL_DISCONNECTED` are on
 * Android's exempted-implicit-broadcast list, so a manifest-declared
 * receiver still gets them even with the app process not running, the same
 * way `BOOT_COMPLETED` works — no foreground app or active service needed
 * for this to fire.
 *
 * Deliberately does nothing on disconnect beyond logging: [PhoneRelayService]
 * already detects the same ACL drop itself (via `DirectRfcommTransport`'s own
 * receiver, the same fix as the watch's direct-connect path) and updates its
 * connection state accordingly — stopping the foreground service outright on
 * every disconnect would fight normal brief drops (e.g. walking out of range
 * for a few seconds) that should just reconnect, not require the phone app
 * or another ACL_CONNECTED to restart relaying.
 */
class AutoRelayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        Log.init(context)

        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            Log.d(TAG, "AutoRelayReceiver: BLUETOOTH_CONNECT not granted yet, ignoring ACL_CONNECTED")
            return
        }

        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            ?: return
        val name = try {
            device.name
        } catch (e: SecurityException) {
            null
        }
        if (!NothingDeviceMatcher.isSupportedEarbuds(name)) return

        Log.i(TAG, "AutoRelayReceiver: $name connected, starting relay")
        PhoneRelayService.start(context.applicationContext, device.address)
    }
}
