package com.nothingx.wear.connection

import android.content.Context

/**
 * Which [com.nothingx.bluetooth.EarbudsTransport] [EarbudsConnectionHolder]
 * should construct: direct RFCOMM from the watch (default, v1's primary
 * path) or the phone relay (`WearRelayTransport`, for watch/earbuds pairs
 * where direct connect doesn't work).
 *
 * Plain synchronous `SharedPreferences`, not the DataStore-backed
 * `DevicePrefs` — deliberately, so [EarbudsConnectionHolder.init] can read
 * the mode synchronously at process start (DataStore reads are suspend-only,
 * and `init()` is called from several non-suspend entry points: `onCreate`,
 * `TileActionActivity`, `EarbudsConnectionService.onStartCommand`).
 * Consequence of that simplicity: switching the toggle in Settings takes
 * effect on the next app/process start, not live — [EarbudsConnectionHolder]
 * doesn't hot-swap a transport out from under an already-collected
 * `StateFlow` reference (`DeviceViewModel` captures `connectionState`/
 * `deviceState` once, non-null, at construction time; a live swap would
 * leave existing collectors watching a now-frozen flow instance). Settings'
 * toggle says so explicitly rather than silently doing nothing.
 */
object TransportModePrefs {
    private const val PREFS_NAME = "nothing_x_transport"
    private const val KEY_USE_RELAY = "use_relay"

    fun useRelay(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_USE_RELAY, false)

    fun setUseRelay(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_USE_RELAY, value).apply()
    }
}
