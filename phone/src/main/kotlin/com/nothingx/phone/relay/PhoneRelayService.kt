package com.nothingx.phone.relay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
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
 * Off by default: only runs while the user has turned relay mode on from
 * `MainActivity`. A foreground service (`connectedDevice` type) the whole
 * time it's on — same pattern as the watch's own
 * `EarbudsConnectionService` — so the process survives in the background
 * without needing to poll for anything; state only gets pushed to the
 * watch when it actually changes, not on a timer.
 */
class PhoneRelayService : Service() {
    private val scope = CoroutineScope(SupervisorJob())
    private var transport: EarbudsTransport? = null
    private var stateJob: Job? = null
    private lateinit var messageClient: MessageClient

    private val messageListener = MessageClient.OnMessageReceivedListener { event -> handleMessage(event) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannelIfNeeded()
        startForeground(NOTIFICATION_ID, buildNotification("Relay starting…"))
        messageClient = Wearable.getMessageClient(this)
        messageClient.addListener(messageListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val t = transport ?: DirectRfcommTransport(this).also {
            transport = it
            observeState(it)
        }
        val address = intent?.getStringExtra(EXTRA_ADDRESS)
        if (address != null) {
            scope.launch { t.connect(address) }
        }
        return START_STICKY
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

    private fun handleMessage(event: MessageEvent) {
        val t = transport ?: run {
            Log.w(TAG, "handleMessage(${event.path}): relay service has no transport yet")
            return
        }
        scope.launch {
            when (event.path) {
                RelayPaths.CMD_CONNECT -> t.connect(RelayCodec.decodeConnect(event.data))
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
        messageClient.removeListener(messageListener)
        stateJob?.cancel()
        val t = transport
        if (t != null) scope.launch { t.disconnect() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
