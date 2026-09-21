package com.nothingx.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

// Pure black + Nothing red, matching something-x's "Pure black · Nothing Red" theme
// and the dot-matrix minimalism of the Nothing brand.
val NothingBlack = Color(0xFF000000)
val NothingRed = Color(0xFFD41F1F)
val NothingSurface = Color(0xFF1C1C1C)
val NothingOnSurfaceDim = Color(0xFF8A8A8A)

private val NothingColorPalette = Colors(
    primary = NothingRed,
    primaryVariant = NothingRed,
    secondary = NothingSurface,
    secondaryVariant = NothingSurface,
    background = NothingBlack,
    surface = NothingSurface,
    error = NothingRed,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = NothingOnSurfaceDim,
    onError = Color.White,
)

@Composable
fun NothingXTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = NothingColorPalette, content = content)
}
