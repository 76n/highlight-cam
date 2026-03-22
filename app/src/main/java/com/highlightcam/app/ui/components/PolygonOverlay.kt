package com.highlightcam.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.highlightcam.app.domain.GoalZone
import com.highlightcam.app.ui.theme.HC
import com.highlightcam.app.ui.theme.HCType

enum class OverlayState {
    IDLE,
    CANDIDATE,
    SAVE,
}

data class ZoneDisplay(
    val zone: GoalZone,
    val tint: Color,
    val state: OverlayState,
)

@Composable
fun PolygonOverlay(
    zones: List<ZoneDisplay>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    val candidateTransition = rememberInfiniteTransition(label = "candidate")
    val candidateAlpha by candidateTransition.animateFloat(
        1f,
        0.5f,
        infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
        label = "c_alpha",
    )

    val saveAlphaA = remember { Animatable(0f) }
    val saveAlphaB = remember { Animatable(0f) }
    val zoneASave = zones.find { it.zone.id == "a" && it.state == OverlayState.SAVE }
    val zoneBSave = zones.find { it.zone.id == "b" && it.state == OverlayState.SAVE }

    LaunchedEffect(zoneASave) {
        if (zoneASave != null) {
            repeat(3) {
                saveAlphaA.animateTo(0.4f, tween(150))
                saveAlphaA.animateTo(0f, tween(150))
            }
        }
    }
    LaunchedEffect(zoneBSave) {
        if (zoneBSave != null) {
            repeat(3) {
                saveAlphaB.animateTo(0.4f, tween(150))
                saveAlphaB.animateTo(0f, tween(150))
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        zones.forEach { display ->
            val pts = display.zone.toPoints()
            if (pts.size < GoalZone.VERTEX_COUNT) return@forEach

            val path =
                Path().apply {
                    moveTo(pts[0].x * size.width, pts[0].y * size.height)
                    for (i in 1 until pts.size) lineTo(pts[i].x * size.width, pts[i].y * size.height)
                    close()
                }

            when (display.state) {
                OverlayState.IDLE -> {
                    drawPath(path, display.tint.copy(alpha = 0.12f))
                    drawPath(path, display.tint, style = Stroke(1.5.dp.toPx()))
                }
                OverlayState.CANDIDATE -> {
                    drawPath(path, HC.amberDim)
                    drawPath(
                        path,
                        HC.amber.copy(alpha = candidateAlpha),
                        style = Stroke(2.5.dp.toPx()),
                    )
                }
                OverlayState.SAVE -> {
                    val flashAlpha =
                        if (display.zone.id == "a") saveAlphaA.value else saveAlphaB.value
                    drawPath(path, Color.White.copy(alpha = flashAlpha))
                    drawPath(path, display.tint, style = Stroke(1.5.dp.toPx()))
                }
            }

            val topCenterX = (pts[0].x + pts[1].x) / 2f * size.width
            val topY = minOf(pts[0].y, pts[1].y) * size.height - 8.dp.toPx()
            val labelColor = if (display.state == OverlayState.CANDIDATE) HC.amber else display.tint
            val textLayout = textMeasurer.measure(display.zone.label, HCType.micro.copy(color = labelColor))
            drawText(
                textLayout,
                topLeft =
                    Offset(
                        topCenterX - textLayout.size.width / 2f,
                        topY - textLayout.size.height,
                    ),
            )
        }
    }
}
