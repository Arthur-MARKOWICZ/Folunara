package com.arthur.ereader.reader.pdf

import com.arthur.ereader.domain.model.FitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfViewportGeometryTest {
    @Test fun `adaptive rendering respects memory pixel limit`() {
        val size = constrainedRenderSize(10_000, 10_000, 4)
        assertTrue(size.width.toLong() * size.height <= 20_000_000L)
        assertEquals(size.width, size.height)
    }
    @Test fun `page fit preserves a portrait page without overflow at one hundred percent`() {
        val geometry = calculatePdfViewportGeometry(600, 1200, 1080, 1800, FitMode.PAGE, 1f)

        assertEquals(900f, geometry.baseWidth, 0.01f)
        assertEquals(1800f, geometry.baseHeight, 0.01f)
        assertEquals(0f, geometry.maxPanX, 0.01f)
        assertEquals(0f, geometry.maxPanY, 0.01f)
    }

    @Test fun `width fit exposes vertical overflow and starts at the top`() {
        val geometry = calculatePdfViewportGeometry(600, 1200, 1080, 1800, FitMode.WIDTH, 1f)

        assertEquals(1080f, geometry.baseWidth, 0.01f)
        assertEquals(2160f, geometry.baseHeight, 0.01f)
        assertEquals(180f, geometry.maxPanY, 0.01f)
        assertEquals(-180f, geometry.initialPanY, 0.01f)
    }

    @Test fun `zoom scales pan bounds and clamps every edge`() {
        val geometry = calculatePdfViewportGeometry(600, 1200, 1080, 1800, FitMode.PAGE, 2f)

        assertEquals(360f, geometry.maxPanX, 0.01f)
        assertEquals(900f, geometry.maxPanY, 0.01f)
        assertEquals(360f, geometry.clampPanX(9999f), 0.01f)
        assertEquals(-900f, geometry.clampPanY(-9999f), 0.01f)
    }

    @Test fun `height fit allows reaching both sides of a landscape page`() {
        val geometry = calculatePdfViewportGeometry(1600, 900, 1080, 1800, FitMode.HEIGHT, 1f)

        assertEquals(3200f, geometry.baseWidth, 0.01f)
        assertEquals(1060f, geometry.maxPanX, 0.01f)
        assertEquals(0f, geometry.maxPanY, 0.01f)
    }
}
