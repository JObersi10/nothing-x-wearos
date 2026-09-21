package com.nothingx.wear.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.nothingx.protocol.EarFitTestResult
import com.nothingx.wear.data.DeviceViewModel

private const val MAX_BASS_LEVEL = 5

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
    val context = LocalContext.current

    // Fit test results arrive asynchronously off the RFCOMM socket, not as a
    // direct response to tapping the chip — a toast is the only way to
    // surface that without the user staring at the screen waiting.
    LaunchedEffect(deviceState.earFitTestResult) {
        val result = deviceState.earFitTestResult ?: return@LaunchedEffect
        Toast.makeText(context, fitTestToastText(result), Toast.LENGTH_LONG).show()
    }

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
                toggleControl = { Switch(checked = deviceState.inEarDetectionEnabled ?: false) },
                colors = ToggleChipDefaults.toggleChipColors(),
            )
        }

        item {
            ToggleChip(
                checked = deviceState.lowLatencyEnabled ?: false,
                onCheckedChange = { viewModel.setLowLatency(it) },
                label = { Text("Low Lag Mode") },
                toggleControl = { Switch(checked = deviceState.lowLatencyEnabled ?: false) },
                colors = ToggleChipDefaults.toggleChipColors(),
            )
        }

        item {
            ToggleChip(
                checked = deviceState.personalizedAncEnabled ?: false,
                onCheckedChange = { viewModel.setPersonalizedAnc(it) },
                label = { Text("Personalized ANC") },
                toggleControl = { Switch(checked = deviceState.personalizedAncEnabled ?: false) },
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
                toggleControl = { Switch(checked = deviceState.bassEnhanceEnabled ?: false) },
                colors = ToggleChipDefaults.toggleChipColors(),
            )
        }
        if (deviceState.bassEnhanceEnabled == true) {
            item {
                InlineSlider(
                    value = deviceState.bassLevel ?: 2,
                    onValueChange = { viewModel.setBassEnhance(true, it) },
                    valueProgression = 0..MAX_BASS_LEVEL,
                    decreaseIcon = { Text("−") },
                    increaseIcon = { Text("+") },
                )
            }
        }

        item {
            Chip(
                onClick = {
                    viewModel.ringBuds(true)
                    Toast.makeText(context, "Ringing earbuds", Toast.LENGTH_SHORT).show()
                },
                label = { Text("Find My Earbuds") },
                colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.surface),
            )
        }

        item {
            Chip(
                onClick = {
                    viewModel.launchEarFitTest()
                    Toast.makeText(
                        context,
                        "Keep both earbuds in your ears during the test",
                        Toast.LENGTH_LONG,
                    ).show()
                },
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

        item {
            Chip(
                onClick = {
                    viewModel.disconnect()
                    Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show()
                },
                label = { Text("Disconnect") },
                // The connection is now kept alive in the background on
                // purpose (so the Tile can control ANC without opening the
                // app) — this is the explicit way to stop that and save
                // battery, not an automatic side effect of navigation anymore.
                colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.surface),
            )
        }
    }
}

private fun fitTestToastText(result: EarFitTestResult): String =
    "Fit test result — L ${result.left} • R ${result.right}"
