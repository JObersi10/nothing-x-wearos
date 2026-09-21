package com.nothingx.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.runtime.collectAsState

@Composable
fun DeviceListScreen(viewModel: DeviceViewModel, onDeviceSelected: (BondedDevice) -> Unit) {
    val devices by viewModel.bondedDevices.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshBondedDevices() }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            ListHeader { Text("Nothing X") }
        }
        if (devices.isEmpty()) {
            item {
                Text(
                    "No paired devices found.\nPair your earbuds in Bluetooth settings first.",
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
        }
        items(devices) { device ->
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
    }
}
