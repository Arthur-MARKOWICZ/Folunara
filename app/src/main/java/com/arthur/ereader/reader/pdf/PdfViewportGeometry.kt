package com.arthur.ereader.reader.pdf

import com.arthur.ereader.domain.model.FitMode
import kotlin.math.max
import kotlin.math.min

data class PdfViewportGeometry(
    val baseWidth: Float,
    val baseHeight: Float,
    val scaledWidth: Float,
    val scaledHeight: Float,
    val maxPanX: Float,
    val maxPanY: Float,
) {
    /** New pages start at the top while remaining horizontally centered. */
    val initialPanX: Float = 0f
    val initialPanY: Float = if (maxPanY > 0f) -maxPanY else 0f

    fun clampPanX(value: Float) = value.coerceIn(-maxPanX, maxPanX)
    fun clampPanY(value: Float) = value.coerceIn(-maxPanY, maxPanY)

}

fun calculatePdfViewportGeometry(
    contentWidth: Int,
    contentHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    fitMode: FitMode,
    zoomScale: Float,
): PdfViewportGeometry {
    if (contentWidth <= 0 || contentHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
        return PdfViewportGeometry(0f, 0f, 0f, 0f, 0f, 0f)
    }
    val widthScale = viewportWidth.toFloat() / contentWidth
    val heightScale = viewportHeight.toFloat() / contentHeight
    val baseScale = when (fitMode) {
        FitMode.PAGE -> min(widthScale, heightScale)
        FitMode.WIDTH -> widthScale
        FitMode.HEIGHT -> heightScale
    }
    val safeZoom = zoomScale.coerceIn(1f, 4f)
    val baseWidth = contentWidth * baseScale
    val baseHeight = contentHeight * baseScale
    val scaledWidth = baseWidth * safeZoom
    val scaledHeight = baseHeight * safeZoom
    return PdfViewportGeometry(
        baseWidth = baseWidth,
        baseHeight = baseHeight,
        scaledWidth = scaledWidth,
        scaledHeight = scaledHeight,
        maxPanX = max(0f, (scaledWidth - viewportWidth) / 2f),
        maxPanY = max(0f, (scaledHeight - viewportHeight) / 2f),
    )
}
