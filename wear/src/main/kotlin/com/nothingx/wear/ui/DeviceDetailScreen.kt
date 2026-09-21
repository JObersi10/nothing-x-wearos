package com.nothingx.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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

    // Deliberately NOT disconnecting on dispose here: that was a real bug —
    // navigating to Settings (a separate nav destination) disposed this
    // composable and tore down the RFCOMM connection out from under it,
    // silently dropping every settings command as "not connected". The
    // connection now lives for the whole device session and is only closed
    // when the user actually leaves it, from DeviceListScreen (see there).
    LaunchedEffect(address) {
        viewModel.connect(address, deviceName)
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

        // Battery first per user request — it's the thing people check most.
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colors.surface)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BatterySlot(letter = "L", percent = deviceState.leftBattery)
                BatterySlot(letter = "C", percent = deviceState.caseBattery)
                BatterySlot(letter = "R", percent = deviceState.rightBattery)
            }
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

        // EQ UI intentionally left out for now (not a priority) — the protocol
        // and viewModel.setEqPreset() plumbing is still there if this comes back.

        item {
            Chip(
                onClick = onOpenSettings,
                label = { Text("⚙ Settings") },
                colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.surface),
            )
        }
    }
}

// Letter badge instead of custom earbud/case pictograms — those were
// hand-drawn vector paths that never got visually checked before shipping,
// and looked it. Text always renders correctly; a circle + bold letter reads
// clearly at watch size and needs no art to get right.
@Composable
private fun BatterySlot(letter: String, percent: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = letter, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Text(text = percentOrDash(percent), fontSize = 13.sp)
    }
}

private fun ancStatusLabel(mode: AncMode): String = when (mode) {
    AncMode.OFF -> "Off"
    AncMode.NOISE_CANCELLATION -> "Noise Cancellation"
    AncMode.TRANSPARENCY -> "Transparency"
}

private fun percentOrDash(value: Int): String = if (value < 0) "—" else "$value%"
