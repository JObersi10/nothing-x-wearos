package com.nothingx.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.ResourceBuilders.AndroidImageResourceByResId
import androidx.wear.protolayout.ResourceBuilders.ImageResource
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.TimelineBuilders.TimelineEntry
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nothingx.protocol.AncMode
import com.nothingx.wear.MainActivity
import com.nothingx.wear.R
import com.nothingx.wear.data.DevicePrefs
import com.nothingx.wear.data.LastKnownDeviceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future

private const val RESOURCES_VERSION = "1"
private const val ANC_ICON_RES_ID = "anc_icon"

private const val NOTHING_BLACK = 0xFF000000.toInt()
private const val NOTHING_SURFACE = 0xFF1C1C1C.toInt()
private const val NOTHING_RED = 0xFFD41F1F.toInt()
private const val NOTHING_WHITE = 0xFFFFFFFF.toInt()
private const val NOTHING_DIM = 0xFF8A8A8A.toInt()

/**
 * Quick-glance Tile in the current edge-to-edge rounded-card Wear OS tile
 * style (the One UI 8 / recent Wear OS look the user pointed to as
 * inspiration: full-bleed card, a colored circular icon badge, bold
 * glanceable text) — not the old cramped square-icon Wear 2.0 tile layout.
 * Built on androidx.wear.protolayout, which is the current (non-deprecated)
 * Tiles layout API; androidx.wear.tiles.TileService is still the correct
 * base class to bind as (see AndroidManifest's BIND_TILE_PROVIDER intent
 * filter) — "protolayout" vs "tiles" here is a library-naming split, not an
 * old-vs-new API split.
 *
 * v1 is read-only — it shows the last-known battery/ANC state cached by
 * DeviceViewModel (see DevicePrefs.cacheState) and taps through to the app.
 * It does not poll the earbuds directly: a Tile can't hold a live RFCOMM
 * connection of its own. Quick in-tile ANC toggling (send a command without
 * opening the app) is a v2 item — it needs either a bound background service
 * holding the RFCOMM connection, or the phone-relay transport, wired up to a
 * Tile action.
 */
class NothingXTileService : TileService() {
    private val prefs by lazy { DevicePrefs(applicationContext) }

    // TileService in androidx.wear.tiles 1.4.0 does not expose a ready-made
    // coroutine scope (an earlier version of this file assumed a `serviceScope`
    // field that doesn't exist here — caught by CI, not guessed correctly the
    // first time). Owning one directly is the safe, version-independent way to
    // bridge onTileRequest's ListenableFuture return to suspending DataStore reads.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = scope.future {
        val state = prefs.lastKnownState.first()

        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                Timeline.Builder()
                    .addTimelineEntry(
                        TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(tileLayout(state))
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val ancIconRes = when (lastAncModeForIcon) {
            AncMode.NOISE_CANCELLATION -> R.drawable.ic_anc_on
            AncMode.TRANSPARENCY -> R.drawable.ic_anc_transparency
            AncMode.OFF -> R.drawable.ic_anc_off
        }
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(
                    ANC_ICON_RES_ID,
                    ImageResource.Builder()
                        .setAndroidResourceByResId(
                            AndroidImageResourceByResId.Builder().setResourceId(ancIconRes).build(),
                        )
                        .build(),
                )
                .build(),
        )
    }

    // onTileResourcesRequest can't suspend to read DataStore the way
    // onTileRequest does (the system may call it independently), so the ANC
    // mode used to pick the resources-request icon is cached from the last
    // onTileRequest call rather than re-read here. Good enough for a
    // read-only glance tile that's rebuilt on every tap-through anyway.
    private var lastAncModeForIcon: AncMode = AncMode.OFF

    private fun tileLayout(state: LastKnownDeviceState): LayoutElementBuilders.LayoutElement {
        lastAncModeForIcon = state.ancMode

        val openAppClickable = Clickable.Builder()
            .setId("open_app")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setClassName(MainActivity::class.java.name)
                            .setPackageName(packageName)
                            .build(),
                    )
                    .build(),
            )
            .build()

        val iconBadge = Box.Builder()
            .setWidth(dp(40f))
            .setHeight(dp(40f))
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(NOTHING_RED))
                            .setCorner(Corner.Builder().setRadius(dp(20f)).build())
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                Image.Builder()
                    .setResourceId(ANC_ICON_RES_ID)
                    .setWidth(dp(22f))
                    .setHeight(dp(22f))
                    .build(),
            )
            .build()

        val textColumn = Column.Builder()
            .addContent(
                Text.Builder()
                    .setText(state.name ?: "Nothing X")
                    .setFontStyle(FontStyle.Builder().setSize(sp(13f)).setColor(argb(NOTHING_DIM)).build())
                    .build(),
            )
            .addContent(
                Text.Builder()
                    .setText(ancLabel(state.ancMode))
                    .setFontStyle(FontStyle.Builder().setSize(sp(17f)).setColor(argb(NOTHING_WHITE)).build())
                    .build(),
            )
            .addContent(ancModeDots(state.ancMode))
            .addContent(
                Text.Builder()
                    .setText(batteryLine(state))
                    .setFontStyle(FontStyle.Builder().setSize(sp(12f)).setColor(argb(NOTHING_DIM)).build())
                    .build(),
            )
            .build()

        val row = Row.Builder()
            .addContent(iconBadge)
            .addContent(
                Box.Builder()
                    .setWidth(dp(12f))
                    .setHeight(dp(1f))
                    .build(),
            )
            .addContent(textColumn)
            .build()

        // Full-bleed rounded card filling the whole tile — the "One UI 8" style
        // edge-to-edge look, rather than a small centered widget with visible
        // system chrome around it.
        val card = Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                Modifiers.Builder()
                    .setClickable(openAppClickable)
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(NOTHING_SURFACE))
                            .setCorner(Corner.Builder().setRadius(dp(28f)).build())
                            .build(),
                    )
                    .setPadding(
                        Padding.Builder()
                            .setStart(dp(16f)).setEnd(dp(16f))
                            .setTop(dp(12f)).setBottom(dp(12f))
                            .build(),
                    )
                    .build(),
            )
            .addContent(row)
            .build()

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(Background.Builder().setColor(argb(NOTHING_BLACK)).build())
                    .build(),
            )
            .addContent(card)
            .build()
    }

    // Small 3-dot segmented indicator (off / ANC / transparency, active one
    // filled red) — a compact echo of the reference pill selector's segmented
    // control, without needing per-segment tap targets or new icon resources
    // since the Tile is read-only for now (see the class doc).
    private fun ancModeDots(current: AncMode): LayoutElementBuilders.LayoutElement {
        val builder = Row.Builder()
        AncMode.entries.forEachIndexed { index, mode ->
            if (index > 0) {
                builder.addContent(Box.Builder().setWidth(dp(5f)).setHeight(dp(1f)).build())
            }
            builder.addContent(
                Box.Builder()
                    .setWidth(dp(7f))
                    .setHeight(dp(7f))
                    .setModifiers(
                        Modifiers.Builder()
                            .setBackground(
                                Background.Builder()
                                    .setColor(argb(if (mode == current) NOTHING_RED else NOTHING_DIM))
                                    .setCorner(Corner.Builder().setRadius(dp(4f)).build())
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
        }
        return builder.build()
    }

    private fun ancLabel(mode: AncMode): String = when (mode) {
        AncMode.OFF -> "ANC Off"
        AncMode.NOISE_CANCELLATION -> "Noise Cancelling"
        AncMode.TRANSPARENCY -> "Transparency"
    }

    private fun batteryLine(state: LastKnownDeviceState): String {
        val left = if (state.leftBattery < 0) "—" else "${state.leftBattery}%"
        val right = if (state.rightBattery < 0) "—" else "${state.rightBattery}%"
        return "L $left • R $right"
    }
}
