package com.nothingx.wear.connection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nothingx.bluetooth.ConnectionState
import com.nothingx.wear.MainActivity
import com.nothingx.wear.R
import com.nothingx.wear.data.DevicePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "earbuds_connection"
private const val NOTIFICATION_ID = 1

/**
 * Foreground service whose only job is to keep this app's process alive in
 * the background so [EarbudsConnectionHolder]'s RFCOMM connection survives
 * after the user leaves the Activity — without this, Android would kill the
 * process fairly quickly once nothing visible is running, and Tile taps
 * (via `TileActionActivity`) would have nothing live to act on.
 *
 * Required foreground notification is deliberately minimal (name + ANC
 * status), matching Android's "don't be annoying" guidance for a
 * long-running connected-device service — not polling anything on its own,
 * just observing the already-live state flow and updating the notification
 * text when it changes.
 */
class EarbudsConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannelIfNeeded()
        startForeground(NOTIFICATION_ID, buildNotification("Connecting…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        EarbudsConnectionHolder.init(applicationContext)
        val explicitAddress = intent?.getStringExtra(EXTRA_ADDRESS)
        scope.launch {
            // Falls back to the last-connected device when the system
            // restarts this service after process death (START_STICKY
            // redelivers with intent == null) rather than just sitting idle.
            val address = explicitAddress ?: DevicePrefs(applicationContext).lastDeviceAddress.first()
            if (address != null) EarbudsConnectionHolder.connect(address)
        }
        observeState()
        return START_STICKY
    }

    private fun observeState() {
        if (observeJob != null) return
        observeJob = scope.launch {
            val prefs = DevicePrefs(applicationContext)
            prefs.lastKnownState.collectLatest { cached ->
                val connectionState = EarbudsConnectionHolder.connectionState
                val statusSuffix = when (connectionState?.value) {
                    is ConnectionState.Connected -> ancLabel(cached.ancMode)
                    is ConnectionState.Connecting -> "Connecting…"
                    else -> "Disconnected"
                }
                val text = (cached.name ?: getString(R.string.app_name)) + " • " + statusSuffix
                notificationManager().notify(NOTIFICATION_ID, buildNotification(text))
            }
        }
    }

    private fun ancLabel(mode: com.nothingx.protocol.AncMode): String = when (mode) {
        com.nothingx.protocol.AncMode.OFF -> "ANC Off"
        com.nothingx.protocol.AncMode.NOISE_CANCELLATION -> "Noise Cancelling"
        com.nothingx.protocol.AncMode.TRANSPARENCY -> "Transparency"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observeJob?.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nothing X")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_earbuds)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Earbuds connection",
            NotificationManager.IMPORTANCE_MIN,
        )
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    companion object {
        private const val EXTRA_ADDRESS = "address"

        /** Starts (or updates) the service, connecting to [address] if given. */
        fun start(context: Context, address: String? = null) {
            val intent = Intent(context, EarbudsConnectionService::class.java)
            if (address != null) intent.putExtra(EXTRA_ADDRESS, address)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EarbudsConnectionService::class.java))
        }
    }
}
