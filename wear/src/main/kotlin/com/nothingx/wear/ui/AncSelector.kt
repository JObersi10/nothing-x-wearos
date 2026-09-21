package com.nothingx.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import com.nothingx.protocol.AncMode
import com.nothingx.wear.R

private data class AncOption(val mode: AncMode, val iconRes: Int, val contentDescription: String)

private val OPTIONS = listOf(
    AncOption(AncMode.OFF, R.drawable.ic_anc_off, "Off"),
    AncOption(AncMode.NOISE_CANCELLATION, R.drawable.ic_anc_on, "Noise cancelling"),
    AncOption(AncMode.TRANSPARENCY, R.drawable.ic_anc_transparency, "Transparency"),
)

/**
 * Pill-shaped ANC mode selector — 3-way segmented control styled after the
 * Galaxy Wearable "Active noise canceling" pill the user pointed to as
 * inspiration. Selected mode gets a filled Nothing-red circle behind its icon.
 */
@Composable
fun AncSelector(current: AncMode, onSelect: (AncMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colors.surface)
            .padding(6.dp),
    ) {
        OPTIONS.forEach { option ->
            val selected = option.mode == current
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colors.primary else Color.Transparent)
                    .clickable { onSelect(option.mode) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(option.iconRes),
                    contentDescription = option.contentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
