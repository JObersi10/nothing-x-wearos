package com.nothingx.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.nothingx.bluetooth.BondedDevice
import com.nothingx.wear.data.DeviceViewModel

/**
 * Device picker for the phone relay path. Replaces the old "tap Buds (phone)
 * and hope the auto-pick on the phone resolves the right device" flow, which
 * a real hardware round found unreliable enough that the user had to open
 * the phone app and pick manually there instead — the exact thing the relay
 * rewrite was supposed to make unnecessary. This screen queries the phone
 * directly for its own bonded-device list (`WearRelayTransport.queryBondedDevices`,
 * answered by `PhoneRelayService`'s `CMD_QUERY_BONDED_DEVICES` handler) and
 * lets the user pick from the watch, same as the direct-connect device list.
 */
@Composable
fun RelayDeviceListScreen(viewModel: DeviceViewModel, onDeviceSelected: (BondedDevice) -> Unit) {
    val devices by viewModel.relayBondedDevices.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.queryRelayBondedDevices()
    }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            ListHeader { Text("Buds (phone)") }
        }
        if (devices.isEmpty()) {
            item {
                Text(
                    "Looking for devices paired to your phone…\n" +
                        "Make sure the earbuds are paired there and the phone app has Bluetooth " +
                        "permission.",
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
        }
        items(devices) { device ->
            DeviceChip(device, onClick = { onDeviceSelected(device) })
        }
    }
}
