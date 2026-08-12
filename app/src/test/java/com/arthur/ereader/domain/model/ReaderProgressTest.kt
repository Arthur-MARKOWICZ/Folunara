package com.arthur.ereader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderProgressTest {
    @Test fun `page progress is one based and clamped`() {
        assertEquals(0.25f, progressForPage(0, 4), 0.001f)
        assertEquals(1f, progressForPage(99, 4), 0.001f)
        assertEquals(0f, progressForPage(0, 0), 0.001f)
    }

    @Test fun `page locator round trips`() {
        val locator = ReaderLocator.page(BookFormat.CBZ, 135)
        assertEquals(135, locator.pageOrNull())
    }

    @Test fun `reading locator stores page and block`() {
        val locator = ReaderLocator.pdfReading(12, 7)
        assertEquals(12, locator.pageOrNull())
        assertEquals(7, locator.blockOrNull())
        assertEquals(2, locator.version)
    }

    @Test fun `bookmark positions compare format version and payload`() {
        val position = ReaderLocator.page(BookFormat.PDF, 3)
        assertEquals(true, position.samePositionAs(ReaderLocator.page(BookFormat.PDF, 3)))
        assertEquals(false, position.samePositionAs(ReaderLocator.page(BookFormat.CBZ, 3)))
        assertEquals(false, position.samePositionAs(ReaderLocator.pdfReading(3, 0)))
    }

    @Test fun `stored reading locator restores its version`() {
        val restored = ReaderLocator.stored(BookFormat.PDF, "{\"page\":9,\"block\":4}")
        assertEquals(2, restored.version)
        assertEquals(4, restored.blockOrNull())
    }

    @Test fun `book override wins over global font size`() {
        assertEquals(1.3f, effectiveEpubFontScale(1f, 1.3f), 0.001f)
        assertEquals(1f, effectiveEpubFontScale(1f, null), 0.001f)
    }

    @Test fun `reader overrides resolve independently by field`() {
        val global = GlobalReaderSettings(
            epub = EpubReaderSettings(fontScale = 1.1f, lineHeight = 1.4f, theme = EpubThemeMode.SEPIA),
            pdf = PdfReaderSettings(pageMode = PdfPageMode.CONTENT_FIT, fitMode = FitMode.WIDTH),
        )
        val overrides = BookReaderOverrides(
            bookId = 42,
            epubFontScale = 1.6f,
            pdfFitMode = FitMode.HEIGHT,
        )

        val effective = effectiveReaderSettings(global, overrides)

        assertEquals(1.6f, effective.epub.fontScale, 0.001f)
        assertEquals(1.4f, effective.epub.lineHeight, 0.001f)
        assertEquals(EpubThemeMode.SEPIA, effective.epub.theme)
        assertEquals(PdfPageMode.CONTENT_FIT, effective.pdf.pageMode)
        assertEquals(FitMode.HEIGHT, effective.pdf.fitMode)
        assertNull(overrides.epubTheme)
    }

    @Test fun `reader numeric settings are clamped to supported ranges`() {
        val normalized = GlobalReaderSettings(
            epub = EpubReaderSettings(fontScale = 9f, lineHeight = 0.2f, pageMargins = -2f),
        ).normalized()

        assertEquals(2f, normalized.epub.fontScale, 0.001f)
        assertEquals(1f, normalized.epub.lineHeight, 0.001f)
        assertEquals(0f, normalized.epub.pageMargins, 0.001f)
        assertEquals(2f, GlobalReaderSettings(pdf = PdfReaderSettings(reading = PdfReadingSettings(fontScale = 8f))).normalized().pdf.reading.fontScale, 0.001f)

        assertEquals(1f, BookReaderOverrides(1, pdfZoomScale = 0.25f).normalized().pdfZoomScale!!, 0.001f)
        assertEquals(4f, BookReaderOverrides(2, pdfZoomScale = 8f).normalized().pdfZoomScale!!, 0.001f)
    }

    @Test fun `pdf reading overrides inherit independently`() {
        val global = GlobalReaderSettings(pdf = PdfReaderSettings(reading = PdfReadingSettings(theme = EpubThemeMode.SEPIA, lineHeight = 1.6f)))
        val effective = effectiveReaderSettings(global, BookReaderOverrides(3, pdfReadingFontScale = 1.4f))

        assertEquals(1.4f, effective.pdf.reading.fontScale, 0.001f)
        assertEquals(1.6f, effective.pdf.reading.lineHeight, 0.001f)
        assertEquals(EpubThemeMode.SEPIA, effective.pdf.reading.theme)
    }

    @Test fun `overrides from different books remain isolated`() {
        val global = GlobalReaderSettings()
        val first = effectiveReaderSettings(global, BookReaderOverrides(1, epubFontScale = 1.5f))
        val second = effectiveReaderSettings(global, BookReaderOverrides(2, pdfFitMode = FitMode.WIDTH))

        assertEquals(1.5f, first.epub.fontScale, 0.001f)
        assertEquals(FitMode.PAGE, first.pdf.fitMode)
        assertEquals(1f, second.epub.fontScale, 0.001f)
        assertEquals(FitMode.WIDTH, second.pdf.fitMode)
    }
}
