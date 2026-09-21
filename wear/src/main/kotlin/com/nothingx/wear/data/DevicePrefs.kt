package com.nothingx.wear.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nothingx.protocol.AncMode
import com.nothingx.protocol.DeviceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nothing_x_prefs")

data class LastKnownDeviceState(
    val name: String?,
    val leftBattery: Int,
    val rightBattery: Int,
    val caseBattery: Int,
    val ancMode: AncMode,
)

/**
 * Remembers the last-connected device address and its last-known state so the
 * app can offer "reconnect" on launch, and so the Tile (which can't hold a
 * live RFCOMM connection of its own) has something to show instead of blank
 * placeholders — see NothingXTileService.
 */
class DevicePrefs(private val context: Context) {
    private val lastDeviceKey = stringPreferencesKey("last_device_address")
    private val lastDeviceNameKey = stringPreferencesKey("last_device_name")
    private val leftBatteryKey = intPreferencesKey("last_left_battery")
    private val rightBatteryKey = intPreferencesKey("last_right_battery")
    private val caseBatteryKey = intPreferencesKey("last_case_battery")
    private val ancModeKey = stringPreferencesKey("last_anc_mode")

    val lastDeviceAddress: Flow<String?> =
        context.dataStore.data.map { it[lastDeviceKey] }

    val lastKnownState: Flow<LastKnownDeviceState> = context.dataStore.data.map { prefs ->
        LastKnownDeviceState(
            name = prefs[lastDeviceNameKey],
            leftBattery = prefs[leftBatteryKey] ?: -1,
            rightBattery = prefs[rightBatteryKey] ?: -1,
            caseBattery = prefs[caseBatteryKey] ?: -1,
            ancMode = prefs[ancModeKey]?.let { runCatching { AncMode.valueOf(it) }.getOrNull() } ?: AncMode.OFF,
        )
    }

    suspend fun setLastDevice(address: String, name: String) {
        context.dataStore.edit {
            it[lastDeviceKey] = address
            it[lastDeviceNameKey] = name
        }
    }

    suspend fun cacheState(state: DeviceState) {
        context.dataStore.edit {
            it[leftBatteryKey] = state.leftBattery
            it[rightBatteryKey] = state.rightBattery
            it[caseBatteryKey] = state.caseBattery
            it[ancModeKey] = state.ancMode.name
        }
    }
}
