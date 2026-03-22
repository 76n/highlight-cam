package com.highlightcam.app.tracking

data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun width(): Float = right - left

    fun height(): Float = bottom - top
}

data class CropWindow(
    val centerX: Float,
    val centerY: Float,
    val scale: Float,
) {
    fun toRect(): CropRect {
        val halfW = 1f / (2f * scale)
        val halfH = 1f / (2f * scale)
        return CropRect(
            left = (centerX - halfW).coerceAtLeast(0f),
            top = (centerY - halfH).coerceAtLeast(0f),
            right = (centerX + halfW).coerceAtMost(1f),
            bottom = (centerY + halfH).coerceAtMost(1f),
        )
    }

    companion object {
        val FULL_FRAME = CropWindow(0.5f, 0.5f, 1.0f)
    }
}

data class AutoFollowConfig(
    val enabled: Boolean = false,
    val smoothingAlpha: Float = DEFAULT_ALPHA,
    val maxScale: Float = 2.0f,
    val spreadPadding: Float = 2.5f,
    val minPlayersToTrack: Int = 2,
) {
    companion object {
        const val DEFAULT_ALPHA = 0.08f
        const val MIN_ALPHA = 0.04f
        const val MAX_ALPHA = 0.20f
    }
}
