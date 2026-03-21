package com.highlightcam.app.ui.setup

import androidx.compose.ui.geometry.Rect
import com.highlightcam.app.domain.GoalZone

fun rectToGoalZone(
    rect: Rect,
    viewWidth: Float,
    viewHeight: Float,
): GoalZone =
    GoalZone(
        xFraction = (rect.left / viewWidth).coerceIn(0f, 1f),
        yFraction = (rect.top / viewHeight).coerceIn(0f, 1f),
        widthFraction = (rect.width / viewWidth).coerceIn(0f, 1f),
        heightFraction = (rect.height / viewHeight).coerceIn(0f, 1f),
    )
