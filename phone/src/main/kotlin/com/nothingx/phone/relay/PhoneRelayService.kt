package com.nothingx.phone.relay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.nothingx.bluetooth.BondedDevices
import com.nothingx.bluetooth.ConnectionState
import com.nothingx.bluetooth.DirectRfcommTransport
import com.nothingx.bluetooth.EarbudsTransport
import com.nothingx.bluetooth.relay.RelayCodec
import com.nothingx.bluetooth.relay.RelayPaths
import com.nothingx.phone.MainActivity
import com.nothingx.protocol.DeviceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "NothingXRelay"
private const val CHANNEL_ID = "earbuds_relay"
private const val NOTIFICATION_ID = 2

/**
 * Phone-side half of the relay path. Holds the one real RFCOMM connection
 * to the earbuds — the same [DirectRfcommTransport] the watch uses when
 * connecting directly, just running here instead, since the phone already
 * has its own normal Classic Bluetooth pairing to the earbuds — and relays
 * commands from / state to the watch over the Wearable Data Layer API
 * (see `RelayProtocol.kt` in :bluetooth for the wire format and why
 * MessageClient vs DataClient were each picked).
 *
 * A [WearableListenerService], not a plain [android.app.Service] — that's
 * what lets Play Services wake this app's process on an incoming relay
 * command even if it's not running and the user never opened the phone app
 * (the manifest declares the `MESSAGE_RECEIVED` intent-filter Play Services
 * looks for). This is what actually makes "tap Buds (phone) on the watch
 * and it just works" true rather than requiring the phone app to already be
 * open — a plain bound `MessageClient.OnMessageReceivedListener`, the first
 * version of this class used, only fires while something already keeps the
 * service alive.
 *
 * Two independent ways relaying starts, both converging on the same
 * [transport]/foreground-service instance:
 *  - [AutoRelayReceiver] starts this service proactively as soon as the
 *    phone's own Bluetooth connects to a matched earbuds device — no watch
 *    or phone interaction needed at all, covers the common case where the
 *    earbuds are just worn normally.
 *  - A `CMD_CONNECT` message from the watch (this class's [onMessageReceived])
 *    starts it on demand if it isn't already running. A blank address in
 *    that message (see [EarbudsConnectionHolder] on the watch side) means
 *    "figure out which paired device to use" rather than the watch needing
 *    to already know a real Bluetooth MAC — it just picks the first matched
 *    bonded device, same as [AutoRelayReceiver].
 *  - [MainActivity]'s manual Start/Stop buttons remain as an explicit
 *    override for either case.
 */
class PhoneRelayService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob())
    private var transport: EarbudsTransport? = null
    private var stateJob: Job? = null
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()
        val t = transportOrCreate()
        val address = intent?.getStringExtra(EXTRA_ADDRESS)
        if (address != null) {
            scope.launch { t.connect(address) }
        }
        return START_STICKY
    }

    override fun onMessageReceived(event: MessageEvent) {
        ensureForeground()
        val t = transportOrCreate()
        scope.launch {
            when (event.path) {
                RelayPaths.CMD_CONNECT -> {
                    val requested = RelayCodec.decodeConnect(event.data)
                    val target = requested.ifBlank { firstMatchedBondedAddress() }
                    if (target != null) {
                        t.connect(target)
                    } else {
                        Log.w(TAG, "CMD_CONNECT: no address given and no matched bonded device found")
                    }
                }
                RelayPaths.CMD_DISCONNECT -> t.disconnect()
                RelayPaths.CMD_SET_ANC_MODE -> t.setAncMode(RelayCodec.decodeAncMode(event.data))
                RelayPaths.CMD_SET_EQ_PRESET -> t.setEqPreset(RelayCodec.decodeEqPreset(event.data))
                RelayPaths.CMD_QUERY_SETTINGS -> t.querySettings()
                RelayPaths.CMD_SET_IN_EAR_DETECTION -> t.setInEarDetection(RelayCodec.decodeBool(event.data))
                RelayPaths.CMD_SET_LOW_LATENCY -> t.setLowLatency(RelayCodec.decodeBool(event.data))
                RelayPaths.CMD_SET_PERSONALIZED_ANC -> t.setPersonalizedAnc(RelayCodec.decodeBool(event.data))
                RelayPaths.CMD_SET_BASS_ENHANCE -> {
                    val (enabled, level) = RelayCodec.decodeBassEnhance(event.data)
                    t.setBassEnhance(enabled, level)
                }
                RelayPaths.CMD_RING_BUDS -> {
                    val (ring, isLeft) = RelayCodec.decodeRingBuds(event.data)
                    t.ringBuds(ring, isLeft)
                }
                RelayPaths.CMD_LAUNCH_EAR_FIT_TEST -> t.launchEarFitTest()
                else -> Log.w(TAG, "unknown relay command path ${event.path}")
            }
        }
    }

    private fun firstMatchedBondedAddress(): String? =
        BondedDevices.list(this).firstOrNull { it.isSupported }?.address

    private fun transportOrCreate(): EarbudsTransport {
        return transport ?: DirectRfcommTransport(this).also {
            transport = it
            observeState(it)
        }
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        foregroundStarted = true
        startForeground(NOTIFICATION_ID, buildNotification("Relay starting…"))
    }

    private fun observeState(t: EarbudsTransport) {
        stateJob?.cancel()
        stateJob = scope.launch {
            launch { t.deviceState.collect { pushDeviceState(it) } }
            launch {
                t.connectionState.collect {
                    pushConnectionState(it)
                    notificationManager().notify(NOTIFICATION_ID, buildNotification(statusText(it)))
                }
            }
        }
    }

    private fun pushDeviceState(state: DeviceState) {
        val request = PutDataMapRequest.create(RelayPaths.DATA_DEVICE_STATE).apply {
            dataMap.putAll(RelayCodec.deviceStateToDataMap(state))
            dataMap.putLong("ts", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(this).putDataItem(request)
            .addOnFailureListener { e -> Log.w(TAG, "pushDeviceState failed: ${e.message}") }
    }

    private fun pushConnectionState(state: ConnectionState) {
        val request = PutDataMapRequest.create(RelayPaths.DATA_CONNECTION_STATE).apply {
            dataMap.putAll(RelayCodec.connectionStateToDataMap(state))
            dataMap.putLong("ts", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(this).putDataItem(request)
            .addOnFailureListener { e -> Log.w(TAG, "pushConnectionState failed: ${e.message}") }
    }

    private fun statusText(state: ConnectionState): String = when (state) {
        is ConnectionState.Idle -> "Idle"
        is ConnectionState.Connecting -> "Connecting…"
        is ConnectionState.Connected -> "Connected — relaying to watch"
        is ConnectionState.Disconnected -> "Disconnected"
        is ConnectionState.Failed -> "Connection failed: ${state.reason}"
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nothing X relay")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Earbuds relay", NotificationManager.IMPORTANCE_MIN)
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    override fun onDestroy() {
        stateJob?.cancel()
        val t = transport
        if (t != null) scope.launch { t.disconnect() }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ADDRESS = "address"

        /** Starts (or updates) the relay service, connecting to [address] if given. */
        fun start(context: Context, address: String? = null) {
            val intent = Intent(context, PhoneRelayService::class.java)
            if (address != null) intent.putExtra(EXTRA_ADDRESS, address)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PhoneRelayService::class.java))
        }
    }
}
