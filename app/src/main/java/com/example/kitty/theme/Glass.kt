package com.example.kitty.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val KittyGlassShape = RoundedCornerShape(16.dp)
val KittyGlassShapeTight = RoundedCornerShape(12.dp)
val KittyGlassShapePill = RoundedCornerShape(999.dp)

/** Solid dark panel — no frosted sheen. */
fun Modifier.kittyGlass(
    shape: Shape = KittyGlassShape,
    elevated: Boolean = false,
): Modifier = this
    .shadow(
        elevation = if (elevated) 12.dp else 6.dp,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.55f),
        spotColor = Color.Black.copy(alpha = 0.4f),
    )
    .clip(shape)
    .background(if (elevated) KittySurfaceElevated else KittySurface)
    .border(1.dp, KittyOutlineSoft, shape)

fun Modifier.kittyGlassSoft(shape: Shape = KittyGlassShape): Modifier = this
    .clip(shape)
    .background(KittyGlass)
    .border(1.dp, KittyOutlineSoft, shape)

fun Modifier.kittyGlassField(shape: Shape = KittyGlassShapeTight): Modifier = this
    .clip(shape)
    .background(KittySurface)
    .border(1.dp, KittyOutlineSoft, shape)

fun Modifier.kittyGlassMirror(
    shape: Shape = KittyGlassShape,
    elevation: Dp = 10.dp,
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.5f),
        spotColor = Color.Black.copy(alpha = 0.35f),
    )
    .clip(shape)
    .background(KittyGlassStrong)
    .border(1.dp, KittyOutline, shape)

/** Flat black page — no radial washes. */
@Composable
fun KittyAtmosphere(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KittyPage),
    )
}

@Composable
fun KittyGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = KittyGlassShape,
    elevated: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.kittyGlass(shape = shape, elevated = elevated),
        content = content,
    )
}
