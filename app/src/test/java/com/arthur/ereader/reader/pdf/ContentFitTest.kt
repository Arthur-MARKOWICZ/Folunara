package com.arthur.ereader.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentFitTest {
    @Test fun `retains a safety gutter around visible content`() {
        val pixels = IntArray(100 * 100) { WHITE }
        for (x in 30..70) for (y in 30..70) pixels[y * 100 + x] = BLACK
        val bounds = ContentFit.detect(100, 100) { x, y -> pixels[y * 100 + x] }
        assertTrue(bounds.left < 30); assertTrue(bounds.top < 30)
        assertTrue(bounds.right > 70); assertTrue(bounds.bottom > 70)
    }
    @Test fun `does not crop an entirely blank page outside its bounds`() {
        val bounds = ContentFit.detect(40, 40) { _, _ -> WHITE }
        assertEquals(0, bounds.left); assertEquals(40, bounds.right)
    }

    @Test fun `never drops thin marks touching any page edge`() {
        val pixels = IntArray(100 * 100) { WHITE }
        pixels[50 * 100] = BLACK
        pixels[50 * 100 + 99] = BLACK
        pixels[50] = BLACK
        pixels[99 * 100 + 50] = BLACK

        val bounds = ContentFit.detect(100, 100) { x, y -> pixels[y * 100 + x] }

        assertEquals(CropBounds(0, 0, 100, 100), bounds)
    }

    @Test fun `keeps an off white page when it cannot identify a safe border`() {
        val offWhite = -0x70708
        val bounds = ContentFit.detect(60, 80) { _, _ -> offWhite }
        assertEquals(CropBounds(0, 0, 60, 80), bounds)
    }

    private companion object {
        const val WHITE = -0x1
        const val BLACK = -0x1000000
    }
}
