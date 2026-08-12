package com.arthur.ereader.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfReflowModelsTest {
    @Test fun `plain prose is reflowable`() {
        val blocks = (0 until 8).map { line ->
            PdfTextBlock("Linha de prosa $line", listOf(PdfTextBounds(60f, 80f + line * 25, 440f, 98f + line * 25)))
        }
        val (classification, diagnostics) = classifyPdfPage(500, 700, blocks, false)

        assertEquals(PdfPageClassification.REFLOWABLE, classification)
        assertTrue(diagnostics.contentFitSafe)
    }

    @Test fun `two columns are preserved`() {
        val left = (0 until 4).map { PdfTextBlock("Esquerda", listOf(PdfTextBounds(30f, 80f + it * 30, 210f, 100f + it * 30))) }
        val right = (0 until 4).map { PdfTextBlock("Direita", listOf(PdfTextBounds(290f, 80f + it * 30, 470f, 100f + it * 30))) }
        val (classification, diagnostics) = classifyPdfPage(500, 700, left + right, false)

        assertEquals(PdfPageClassification.COMPLEX, classification)
        assertTrue(diagnostics.suspectedColumns)
    }

    @Test fun `text beyond declared page is damaged and disables crop`() {
        val block = PdfTextBlock("fim da linha", listOf(PdfTextBounds(430f, 100f, 530f, 120f)))
        val (classification, diagnostics) = classifyPdfPage(500, 700, listOf(block), false)

        assertEquals(PdfPageClassification.DAMAGED, classification)
        assertTrue(diagnostics.textBeyondPage)
        assertFalse(diagnostics.contentFitSafe)
    }

    @Test fun `scan without text is image only`() {
        val (classification, _) = classifyPdfPage(500, 700, emptyList(), true)
        assertEquals(PdfPageClassification.IMAGE_ONLY, classification)
    }
}
