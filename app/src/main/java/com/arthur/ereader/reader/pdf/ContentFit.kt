package com.arthur.ereader.reader.pdf

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

data class CropBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun crop(source: Bitmap): Bitmap {
        if (!isValidFor(source.width, source.height)) return source
        if (left == 0 && top == 0 && right == source.width && bottom == source.height) return source
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }

    fun isValidFor(width: Int, height: Int) =
        left >= 0 && top >= 0 && right <= width && bottom <= height && right > left && bottom > top
}

data class ContentFitAnalysis(val bounds: CropBounds, val safe: Boolean)

/** Conservative visual crop: scans every pixel and keeps a 4% safety gutter. */
object ContentFit {
    fun detect(bitmap: Bitmap): CropBounds {
        val row = IntArray(bitmap.width)
        return detectRows(bitmap.width, bitmap.height) { y ->
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            row
        }
    }

    fun analyze(bitmap: Bitmap): ContentFitAnalysis {
        val bounds = detect(bitmap)
        val safety = max(8, min(bitmap.width, bitmap.height) / 25)
        val safe = bounds.left >= safety / 2 && bounds.top >= safety / 2 &&
            bounds.right <= bitmap.width - safety / 2 && bounds.bottom <= bitmap.height - safety / 2
        return ContentFitAnalysis(bounds, safe)
    }

    internal fun detect(width: Int, height: Int, pixelAt: (Int, Int) -> Int): CropBounds {
        return detectRows(width, height) { y -> IntArray(width) { x -> pixelAt(x, y) } }
    }

    private fun detectRows(width: Int, height: Int, rowAt: (Int) -> IntArray): CropBounds {
        if (width <= 0 || height <= 0) return CropBounds(0, 0, max(0, width), max(0, height))
        fun useful(color: Int): Boolean {
            val red = color shr 16 and 0xFF
            val green = color shr 8 and 0xFF
            val blue = color and 0xFF
            return red < 250 || green < 250 || blue < 250
        }
        var rawTop = height
        var rawBottom = -1
        var rawLeft = width
        var rawRight = -1
        for (y in 0 until height) {
            val row = rowAt(y)
            for (x in 0 until width) {
                if (useful(row[x])) {
                    rawTop = min(rawTop, y)
                    rawBottom = max(rawBottom, y)
                    rawLeft = min(rawLeft, x)
                    rawRight = max(rawRight, x)
                }
            }
        }
        if (rawRight < rawLeft || rawBottom < rawTop) return CropBounds(0, 0, width, height)
        val gutter = max(8, min(width, height) / 25)
        return CropBounds(
            max(0, rawLeft - gutter),
            max(0, rawTop - gutter),
            min(width, rawRight + gutter + 1),
            min(height, rawBottom + gutter + 1),
        ).takeIf { it.isValidFor(width, height) } ?: CropBounds(0, 0, width, height)
    }
}
