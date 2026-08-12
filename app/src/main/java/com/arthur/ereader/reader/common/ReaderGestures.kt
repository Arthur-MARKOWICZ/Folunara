package com.arthur.ereader.reader.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

/** Keeps reader interactions consistent while allowing zoomed pages to consume horizontal drags. */
fun Modifier.readerGestures(
    viewport: () -> IntSize,
    scale: () -> Float,
    onZoomPan: (zoom: Float, panX: Float, panY: Float) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    canTurnPage: (dragX: Float) -> Boolean = { scale() <= 1.01f },
    onGestureEnd: () -> Unit = {},
): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures(
            onTap = { onToggleControls() },
            onDoubleTap = { point -> if (point.x < size.width / 2f) onPrevious() else onNext() },
        )
    }
    .pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var totalDragX = 0f
            var transformed = false
            var maxPointers = 1
            var pendingPan = Offset.Zero
            do {
                val event = awaitPointerEvent()
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                maxPointers = maxOf(maxPointers, event.changes.count { it.pressed })
                pendingPan += pan
                val wasTransformed = transformed
                val crossedTouchSlop = pendingPan.getDistance() > viewConfiguration.touchSlop
                if (transformed || zoom != 1f || maxPointers > 1 || crossedTouchSlop) {
                    transformed = true
                    val appliedPan = if (wasTransformed) pan else pendingPan
                    pendingPan = Offset.Zero
                    totalDragX += appliedPan.x
                    onZoomPan(zoom, appliedPan.x, appliedPan.y)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
            if (transformed) {
                if (maxPointers == 1 && canTurnPage(totalDragX)) when (pageTurnForSwipe(totalDragX, true, viewport().width)) {
                    -1 -> onPrevious()
                    1 -> onNext()
                }
                onGestureEnd()
            }
        }
    }

fun Modifier.readerTaps(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
    doubleTapEnabled: () -> Boolean = { true },
): Modifier = pointerInput(Unit) {
    detectTapGestures(
        onTap = { onToggleControls() },
        onDoubleTap = { point ->
            if (doubleTapEnabled()) {
                if (point.x < size.width / 2f) onPrevious() else onNext()
            }
        },
    )
}

/** Pure swipe decision, kept testable and shared by image readers. */
fun pageTurnForSwipe(totalDragX: Float, scale: Float, width: Int): Int {
    return pageTurnForSwipe(totalDragX, scale <= 1.01f, width)
}

fun pageTurnForSwipe(totalDragX: Float, canTurnPage: Boolean, width: Int): Int {
    if (!canTurnPage || width <= 0 || abs(totalDragX) < width * 0.18f) return 0
    return if (totalDragX < 0f) 1 else -1
}

fun isDoubleTap(
    previousTime: Long,
    currentTime: Long,
    deltaX: Float,
    deltaY: Float,
    timeoutMillis: Long,
    slop: Float,
): Boolean {
    if (previousTime < 0 || currentTime < previousTime) return false
    if (currentTime - previousTime > timeoutMillis) return false
    return deltaX * deltaX + deltaY * deltaY <= slop * slop
}
