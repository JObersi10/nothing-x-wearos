package com.nothingx.wear

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.nothingx.bluetooth.ConnectionState
import com.nothingx.wear.connection.EarbudsConnectionHolder
import com.nothingx.wear.data.DeviceViewModel
import com.nothingx.wear.ui.DeviceDetailScreen
import com.nothingx.wear.ui.DeviceListScreen
import com.nothingx.wear.ui.PermissionGate
import com.nothingx.wear.ui.RelayDeviceListScreen
import com.nothingx.wear.ui.SettingsScreen
import com.nothingx.wear.ui.theme.NothingXTheme

private const val TAG = "NothingX"

class MainActivity : ComponentActivity() {
    private val viewModel: DeviceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NothingXTheme {
                PermissionGate {
                    NothingXApp(viewModel)
                }
            }
        }
    }
}

@Composable
private fun NothingXApp(viewModel: DeviceViewModel) {
    val navController = rememberSwipeDismissableNavController()
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val pendingDevice by viewModel.pendingDevice.collectAsState()

    // Auto-navigate to the device detail screen the moment a connection
    // actually completes — not the moment it starts. Covers both a tap on
    // the device list (connect() sets pendingDevice, this effect fires once
    // connectionState flips to Connected) and reopening the app while the
    // last device is still connected in the background (DeviceViewModel
    // resumes it automatically on init, same effect handles the jump).
    LaunchedEffect(connectionState, pendingDevice) {
        val device = pendingDevice ?: return@LaunchedEffect
        if (connectionState is ConnectionState.Connected) {
            val route = "detail/${device.address}/${Uri.encode(device.name)}"
            if (navController.currentBackStackEntry?.destination?.route != "detail/{address}/{name}") {
                navController.navigate(route)
            }
            // Once navigated, stop watching — otherwise a later disconnect/
            // reconnect cycle (e.g. the earbuds briefly drop out of range
            // while the user is on the Settings screen) would yank them back
            // to the detail screen again on its own.
            viewModel.clearPendingDevice()
        }
    }

    MaterialTheme {
        SwipeDismissableNavHost(navController = navController, startDestination = "list") {
            composable("list") {
                DeviceListScreen(viewModel) { device ->
                    Log.i(TAG, "DeviceListScreen: tapped \"${device.name}\" (${device.address})")
                    if (device.address == EarbudsConnectionHolder.RELAY_TARGET_ADDRESS) {
                        Log.i(TAG, "DeviceListScreen: navigating to relayPicker")
                        navController.navigate("relayPicker")
                    } else {
                        Toast.makeText(context, "Connecting to ${device.name}…", Toast.LENGTH_SHORT).show()
                        viewModel.connect(device.address, device.name)
                    }
                }
            }
            composable("relayPicker") {
                RelayDeviceListScreen(viewModel) { device ->
                    Log.i(TAG, "RelayDeviceListScreen: picked \"${device.name}\" (${device.address})")
                    Toast.makeText(context, "Connecting to ${device.name}…", Toast.LENGTH_SHORT).show()
                    viewModel.connect(EarbudsConnectionHolder.RELAY_ADDRESS_PREFIX + device.address, device.name)
                }
            }
            composable("detail/{address}/{name}") { backStackEntry ->
                val address = backStackEntry.arguments?.getString("address").orEmpty()
                val name = backStackEntry.arguments?.getString("name").orEmpty()
                DeviceDetailScreen(viewModel, address, name, onOpenSettings = { navController.navigate("settings") })
            }
            composable("settings") {
                SettingsScreen(viewModel)
            }
        }
    }
}
