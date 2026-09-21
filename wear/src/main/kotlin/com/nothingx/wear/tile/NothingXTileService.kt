package com.nothingx.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.TimelineBuilders.TimelineEntry
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nothingx.protocol.AncMode
import com.nothingx.wear.MainActivity
import com.nothingx.wear.data.DevicePrefs
import com.nothingx.wear.data.LastKnownDeviceState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future

private const val RESOURCES_VERSION = "1"

private const val NOTHING_BLACK = 0xFF000000.toInt()
private const val NOTHING_SURFACE = 0xFF1C1C1C.toInt()
private const val NOTHING_RED = 0xFFD41F1F.toInt()
private const val NOTHING_WHITE = 0xFFFFFFFF.toInt()
private const val NOTHING_DIM = 0xFF8A8A8A.toInt()

/**
 * Quick-glance Tile styled after the rounded-pill "quick settings" bubble the
 * user pointed to as inspiration (One UI 8 style). v1 is read-only — it shows
 * the last-known battery/ANC state cached by DeviceViewModel (see
 * DevicePrefs.cacheState) and taps through to the app. It does not poll the
 * earbuds directly: a Tile can't hold a live RFCOMM connection of its own.
 * Quick in-tile ANC toggling (send a command without opening the app) is a
 * v2 item — it needs either a bound background service holding the RFCOMM
 * connection, or the phone-relay transport, wired up to a Tile action.
 */
class NothingXTileService : TileService() {
    private val prefs by lazy { DevicePrefs(applicationContext) }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = serviceScope.future {
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
    ): ListenableFuture<ResourceBuilders.Resources> = Futures.immediateFuture(
        ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build(),
    )

    private fun tileLayout(state: LastKnownDeviceState): LayoutElementBuilders.LayoutElement {
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

        val pill = Box.Builder()
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
                            .setStart(dp(18f)).setEnd(dp(18f))
                            .setTop(dp(12f)).setBottom(dp(12f))
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                Column.Builder()
                    .addContent(
                        Text.Builder()
                            .setText(state.name ?: "Nothing X")
                            .setFontStyle(FontStyle.Builder().setSize(dp(15f)).setColor(argb(NOTHING_WHITE)).build())
                            .build(),
                    )
                    .addContent(
                        Text.Builder()
                            .setText(ancLabel(state.ancMode))
                            .setFontStyle(FontStyle.Builder().setSize(dp(13f)).setColor(argb(NOTHING_RED)).build())
                            .build(),
                    )
                    .addContent(
                        Text.Builder()
                            .setText(batteryLine(state))
                            .setFontStyle(FontStyle.Builder().setSize(dp(12f)).setColor(argb(NOTHING_DIM)).build())
                            .build(),
                    )
                    .build(),
            )
            .build()

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(Background.Builder().setColor(argb(NOTHING_BLACK)).build())
                    .build(),
            )
            .addContent(pill)
            .build()
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
