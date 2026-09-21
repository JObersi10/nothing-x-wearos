package com.nothingx.phone

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nothingx.bluetooth.BondedDevice
import com.nothingx.bluetooth.BondedDevices
import com.nothingx.phone.relay.PhoneRelayService

private val REQUIRED_PERMISSIONS: Array<String> = buildList {
    add(Manifest.permission.BLUETOOTH_CONNECT)
    add(Manifest.permission.BLUETOOTH_SCAN)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private const val PERMISSION_REQUEST_CODE = 1

/**
 * Phone-side companion app. Lets the user turn on relay mode: the phone
 * connects to the earbuds itself (over its own normal Classic Bluetooth
 * pairing) and [PhoneRelayService] relays commands/state to and from the
 * watch, for the watch/earbuds pairs where a direct watch connection
 * doesn't work. The watch still connects directly by default — this is
 * the fallback path, and stays fully off until the user explicitly starts
 * it here (see `TransportModePrefs` on the watch side for the matching
 * "use phone relay" toggle).
 *
 * Plain [Activity], not AppCompatActivity: this had a real crash-on-launch
 * bug once already (AppCompatActivity requires a Theme.AppCompat
 * descendant theme; the manifest uses the platform's Theme.DeviceDefault).
 * This screen doesn't need any AppCompat feature, so staying on plain
 * Activity avoids the whole bug class. Plain Views for the same reason —
 * no Compose dependency needed for a screen this simple, and one less
 * toolchain surface for this module to carry.
 */
class MainActivity : Activity() {
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
        } else {
            refreshDeviceList()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (hasPermissions()) {
                refreshDeviceList()
            } else {
                statusText.text = "Bluetooth permission is required to relay to your earbuds."
            }
        }
    }

    private fun hasPermissions(): Boolean = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private lateinit var deviceListContainer: LinearLayout

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        root.addView(
            TextView(this).apply {
                text = "Nothing X — phone relay"
                textSize = 20f
                setPadding(0, 0, 0, 24)
            },
        )
        root.addView(
            TextView(this).apply {
                text = "The watch connects to your earbuds directly by default — this app is only " +
                    "needed if you turn on \"Use phone relay\" in the watch app's Settings. " +
                    "Pick a device below to start relaying."
                setPadding(0, 0, 0, 32)
            },
        )

        statusText = TextView(this).apply { setPadding(0, 0, 0, 24) }
        root.addView(statusText)

        deviceListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(deviceListContainer)

        root.addView(
            Button(this).apply {
                text = "Refresh device list"
                setOnClickListener { refreshDeviceList() }
            },
        )

        root.addView(
            Button(this).apply {
                text = "Stop relay"
                setOnClickListener {
                    PhoneRelayService.stop(this@MainActivity)
                    statusText.text = "Relay stopped."
                    Toast.makeText(this@MainActivity, "Relay stopped", Toast.LENGTH_SHORT).show()
                }
            },
        )

        setContentView(root)
    }

    private fun refreshDeviceList() {
        deviceListContainer.removeAllViews()
        val devices = BondedDevices.list(this)
        val matched = devices.filter { it.isSupported }
        if (matched.isEmpty()) {
            deviceListContainer.addView(
                TextView(this).apply {
                    text = "No paired Nothing/CMF earbuds found. Pair them in phone Bluetooth " +
                        "settings first, then refresh."
                },
            )
            return
        }
        for (device in matched) {
            deviceListContainer.addView(deviceRow(device))
        }
    }

    private fun deviceRow(device: BondedDevice): Button = Button(this).apply {
        gravity = Gravity.START
        text = device.name + if (device.isUnverified) " (unverified)" else ""
        setOnClickListener {
            PhoneRelayService.start(this@MainActivity, device.address)
            statusText.text = "Starting relay to ${device.name}…"
            Toast.makeText(this@MainActivity, "Starting relay to ${device.name}", Toast.LENGTH_SHORT).show()
        }
    }
}
