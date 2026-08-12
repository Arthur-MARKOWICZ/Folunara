package com.arthur.ereader.reader.pdf

import com.arthur.ereader.domain.model.Book

data class PdfTextBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

data class PdfTextBlock(val text: String, val bounds: List<PdfTextBounds>) {
    val union: PdfTextBounds?
        get() = bounds.takeIf { it.isNotEmpty() }?.let { all ->
            PdfTextBounds(
                all.minOf { it.left },
                all.minOf { it.top },
                all.maxOf { it.right },
                all.maxOf { it.bottom },
            )
        }
}

enum class PdfPageClassification { REFLOWABLE, COMPLEX, IMAGE_ONLY, DAMAGED }

data class PdfPageDiagnostics(
    val pageWidth: Int,
    val pageHeight: Int,
    val textBeyondPage: Boolean,
    val textTouchesSafetyMargin: Boolean,
    val hasImages: Boolean,
    val suspectedColumns: Boolean,
    val reasons: List<String>,
) {
    val contentFitSafe: Boolean get() =
        !textBeyondPage && !textTouchesSafetyMargin && reasons.none {
            it == "Nenhuma camada textual utilizável" || it == "A camada textual parece danificada"
        }
}

data class PdfExtractedPage(
    val index: Int,
    val width: Int,
    val height: Int,
    val blocks: List<PdfTextBlock>,
    val classification: PdfPageClassification,
    val diagnostics: PdfPageDiagnostics,
) {
    val reflowText: String
        get() = blocks.asSequence()
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
            .replace(Regex("(?<=\\p{Ll})-\\s*\\n\\s*(?=\\p{Ll})"), "")
}

data class PdfReflowDocument(
    val bookId: Long,
    val title: String,
    val fingerprint: String,
    val extractorVersion: Int,
    val pages: List<PdfExtractedPage>,
)

data class PdfExportReport(
    val reflowablePages: Int,
    val preservedPages: Int,
    val imageOnlyPages: Int,
    val damagedPages: Int,
    val estimatedBytes: Long,
) {
    val requiresConfirmation: Boolean get() = preservedPages > 0 || damagedPages > 0
}

interface PdfContentExtractor {
    val version: Int
    suspend fun extract(book: Book, fingerprint: String): PdfReflowDocument
}

internal fun classifyPdfPage(
    pageWidth: Int,
    pageHeight: Int,
    blocks: List<PdfTextBlock>,
    hasImages: Boolean,
): Pair<PdfPageClassification, PdfPageDiagnostics> {
    val bounds = blocks.flatMap { it.bounds }
    val tolerance = 0.5f
    val beyond = bounds.any {
        it.left < -tolerance || it.top < -tolerance ||
            it.right > pageWidth + tolerance || it.bottom > pageHeight + tolerance
    }
    val safetyX = pageWidth * 0.0125f
    val safetyY = pageHeight * 0.0125f
    val touches = bounds.any {
        it.left <= safetyX || it.right >= pageWidth - safetyX ||
            it.top <= safetyY || it.bottom >= pageHeight - safetyY
    }
    val columns = suspectedColumns(bounds, pageWidth)
    val text = blocks.joinToString(" ") { it.text }.trim()
    val replacementCharacters = text.count { it == '\uFFFD' }
    val damagedText = replacementCharacters > 0 || (text.isNotEmpty() && bounds.isEmpty())
    val reasons = buildList {
        if (beyond) add("Texto ultrapassa a área declarada da página")
        if (touches) add("Conteúdo encosta na margem de segurança")
        if (columns) add("Possível diagrama, tabela ou múltiplas colunas")
        if (hasImages) add("A página contém imagens")
        if (damagedText) add("A camada textual parece danificada")
        if (text.isEmpty()) add("Nenhuma camada textual utilizável")
    }
    val classification = when {
        damagedText || beyond -> PdfPageClassification.DAMAGED
        text.isEmpty() && hasImages -> PdfPageClassification.IMAGE_ONLY
        text.isEmpty() -> PdfPageClassification.DAMAGED
        hasImages || columns -> PdfPageClassification.COMPLEX
        else -> PdfPageClassification.REFLOWABLE
    }
    return classification to PdfPageDiagnostics(
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        textBeyondPage = beyond,
        textTouchesSafetyMargin = touches,
        hasImages = hasImages,
        suspectedColumns = columns,
        reasons = reasons,
    )
}

private fun suspectedColumns(bounds: List<PdfTextBounds>, pageWidth: Int): Boolean {
    if (bounds.size < 6 || pageWidth <= 0) return false
    val leftColumn = bounds.filter { it.right <= pageWidth * 0.58f }
    val rightColumn = bounds.filter { it.left >= pageWidth * 0.42f }
    if (leftColumn.size < 3 || rightColumn.size < 3) return false
    return leftColumn.any { left ->
        rightColumn.any { right ->
            left.top < right.bottom && right.top < left.bottom
        }
    }
}

fun PdfReflowDocument.exportReport(): PdfExportReport {
    val reflowable = pages.count { it.classification == PdfPageClassification.REFLOWABLE }
    val imageOnly = pages.count { it.classification == PdfPageClassification.IMAGE_ONLY }
    val damaged = pages.count { it.classification == PdfPageClassification.DAMAGED }
    val preserved = pages.size - reflowable
    return PdfExportReport(
        reflowablePages = reflowable,
        preservedPages = preserved,
        imageOnlyPages = imageOnly,
        damagedPages = damaged,
        estimatedBytes = reflowable * 16_384L + preserved * 1_500_000L,
    )
}
