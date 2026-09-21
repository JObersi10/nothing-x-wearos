package com.nothingx.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.nothingx.bluetooth.ConnectionState
import com.nothingx.protocol.AncMode
import com.nothingx.wear.data.DeviceViewModel

@Composable
fun DeviceDetailScreen(
    viewModel: DeviceViewModel,
    address: String,
    deviceName: String,
    onOpenSettings: () -> Unit,
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val deviceState by viewModel.deviceState.collectAsState()

    DisposableEffect(address) {
        viewModel.connect(address, deviceName)
        onDispose { viewModel.disconnect() }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = deviceName,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            val statusText = when (val s = connectionState) {
                is ConnectionState.Idle -> "Not connected"
                is ConnectionState.Connecting -> "Connecting… (${s.channelsTried})"
                is ConnectionState.Connected -> if (deviceState.activated) "Connected" else "Activating…"
                is ConnectionState.Disconnected -> "Disconnected"
                is ConnectionState.Failed -> "Couldn't connect"
            }
            Text(
                text = statusText,
                fontSize = 12.sp,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Dark elevated card per feature area — the Nothing X app's own layout
        // language (title + status line, grouped in a rounded dark card),
        // adapted for a round screen rather than copied from the phone layout.
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colors.surface)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Noise Cancellation",
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = ancStatusLabel(deviceState.ancMode),
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                AncSelector(
                    current = deviceState.ancMode,
                    onSelect = { viewModel.setAncMode(it) },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colors.surface)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = batteryLine(deviceState.leftBattery, deviceState.rightBattery),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Case ${percentOrDash(deviceState.caseBattery)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // EQ UI intentionally left out for now (not a priority) — the protocol
        // and viewModel.setEqPreset() plumbing is still there if this comes back.

        item {
            Chip(
                onClick = onOpenSettings,
                label = { Text("Settings") },
                colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.surface),
            )
        }
    }
}

private fun ancStatusLabel(mode: AncMode): String = when (mode) {
    AncMode.OFF -> "Off"
    AncMode.NOISE_CANCELLATION -> "Noise Cancellation"
    AncMode.TRANSPARENCY -> "Transparency"
}

private fun batteryLine(left: Int, right: Int): String =
    "L ${percentOrDash(left)} • R ${percentOrDash(right)}"

private fun percentOrDash(value: Int): String = if (value < 0) "—" else "$value%"
