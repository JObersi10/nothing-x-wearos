package com.nothingx.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.nothingx.wear.data.DeviceViewModel

/**
 * Settings screen — card-per-feature list, styled after the official Nothing X
 * app's dark elevated-card language (title + status/subtitle line) the user
 * showed as inspiration, adapted for a round watch screen and Wear Compose's
 * own toggle/chip components rather than reproducing the phone layout.
 *
 * Every toggle here fires a real mined command (see Commands.kt's doc
 * comment on ear-web provenance) — nothing here is a placeholder.
 */
@Composable
fun SettingsScreen(viewModel: DeviceViewModel) {
    val deviceState by viewModel.deviceState.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { ListHeader { Text("Settings") } }

        item {
            Text(
                text = "Firmware ${deviceState.firmwareVersion ?: "—"}",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            ToggleChip(
                checked = deviceState.inEarDetectionEnabled ?: false,
                onCheckedChange = { viewModel.setInEarDetection(it) },
                label = { Text("In-Ear Detection") },
                toggleControl = { androidx.wear.compose.material.Switch(checked = deviceState.inEarDetectionEnabled ?: false) },
                colors = ToggleChipDefaults.toggleChipColors(),
            )
        }

        item {
            ToggleChip(
                checked = deviceState.lowLatencyEnabled ?: false,
                onCheckedChange = { viewModel.setLowLatency(it) },
                label = { Text("Low Lag Mode") },
                toggleControl = { androidx.wear.compose.material.Switch(checked = deviceState.lowLatencyEnabled ?: false) },
                colors = ToggleChipDefaults.toggleChipColors(),
            )
        }

        item {
            ToggleChip(
                checked = deviceState.personalizedAncEnabled ?: false,
                onCheckedChange = { viewModel.setPersonalizedAnc(it) },
                label = { Text("Personalized ANC") },
                toggleControl = {
                    androidx.wear.compose.material.Switch(checked = deviceState.personalizedAncEnabled ?: false)
                },
                colors = ToggleChipDefaults.toggleChipColors(),
            )
        }

        item {
            ToggleChip(
                checked = deviceState.bassEnhanceEnabled ?: false,
                onCheckedChange = { viewModel.setBassEnhance(it, deviceState.bassLevel ?: 2) },
                label = { Text("Ultra Bass") },
                secondaryLabel = {
                    Text(if (deviceState.bassEnhanceEnabled == true) "Level ${deviceState.bassLevel ?: 2}" else "Off")
                },
                toggleControl = {
                    androidx.wear.compose.material.Switch(checked = deviceState.bassEnhanceEnabled ?: false)
                },
                colors = ToggleChipDefaults.toggleChipColors(),
            )
        }

        item {
            Chip(
                onClick = { viewModel.ringBuds(true) },
                label = { Text("Find My Earbuds") },
                colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.surface),
            )
        }

        item {
            Chip(
                onClick = { viewModel.launchEarFitTest() },
                label = { Text("Ear Tip Fit Test") },
                // ear-web reads these two bytes but never documents what the
                // values mean beyond "left/right result" — showing the raw
                // numbers rather than guessing at a Good/Loose label.
                secondaryLabel = deviceState.earFitTestResult?.let { result ->
                    { Text("L ${result.left} • R ${result.right}") }
                },
                colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.surface),
            )
        }

        item {
            Text(
                text = "Controls: " + (deviceState.gestureCount?.let { "$it configured" } ?: "—"),
                fontSize = 12.sp,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Text(
                text = "Gesture editing isn't built yet — read-only for now.",
                fontSize = 10.sp,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
