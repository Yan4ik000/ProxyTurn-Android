package com.wdtt.client.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.verticalScrollEdgeFade(
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    fadeHeight: Dp = 32.dp,
    innerEdgeOffset: Dp = 0.dp
): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    val visibleFadeHeight = (fadeHeight - innerEdgeOffset).coerceAtLeast(0.dp)
    val fadeFraction = (visibleFadeHeight.toPx() / size.height).coerceIn(0f, 0.5f)
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to if (canScrollBackward) Color.Transparent else Color.Black,
                fadeFraction to Color.Black,
                1f - fadeFraction to Color.Black,
                1f to if (canScrollForward) Color.Transparent else Color.Black
            )
        ),
        blendMode = BlendMode.DstIn
    )
}
