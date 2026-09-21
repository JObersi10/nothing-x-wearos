package com.nothingx.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.nothingx.bluetooth.BondedDevice
import com.nothingx.wear.data.DeviceViewModel

@Composable
fun DeviceListScreen(viewModel: DeviceViewModel, onDeviceSelected: (BondedDevice) -> Unit) {
    val devices by viewModel.bondedDevices.collectAsState()
    // Default to only the devices NothingDeviceMatcher recognizes — the point
    // is the user shouldn't have to guess which bonded device is their
    // earbuds among every paired accessory. "Show all" is the fallback for
    // when a device's Bluetooth name doesn't match any known pattern.
    var showAll by remember { mutableStateOf(false) }

    // Deliberately does NOT disconnect on returning here anymore. It used to
    // (navigating back to the list was treated as "left this device"), but
    // that directly fought the persistent background connection the Tile
    // needs to control ANC without opening the app — every trip back to
    // this screen would have killed it. The connection is now
    // intentionally long-lived; see EarbudsConnectionHolder's doc comment.
    // Explicit disconnect lives in SettingsScreen instead.
    LaunchedEffect(Unit) {
        viewModel.refreshBondedDevices()
    }

    val supported = devices.filter { it.isSupported }
    val visible = if (showAll) devices else supported

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            ListHeader { Text("Nothing X") }
        }
        if (visible.isEmpty()) {
            item {
                Text(
                    if (devices.isEmpty()) {
                        "No paired devices found.\nPair your earbuds in Bluetooth settings first."
                    } else {
                        "No paired device looks like Nothing/CMF earbuds.\nTap below to see everything paired."
                    },
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
        }
        items(visible) { device ->
            Chip(
                onClick = { onDeviceSelected(device) },
                label = {
                    Text(
                        if (device.isUnverified) "${device.name} (unverified)" else device.name,
                    )
                },
                colors = ChipDefaults.chipColors(
                    backgroundColor = if (device.isSupported) {
                        MaterialTheme.colors.primary
                    } else {
                        MaterialTheme.colors.surface
                    },
                ),
            )
        }
        if (!showAll && supported.size != devices.size) {
            item {
                Chip(
                    onClick = { showAll = true },
                    label = { Text("Show all paired devices") },
                    colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.surface),
                )
            }
        }
    }
}
