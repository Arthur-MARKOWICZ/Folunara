package com.arthur.ereader.reader.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import androidx.core.net.toUri
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.SandboxedPdfLoader
import com.arthur.ereader.domain.model.Book
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalPdfApi::class)
@Singleton
class AndroidxPdfContentExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) : PdfContentExtractor {
    override val version: Int = 1
    private val loader = SandboxedPdfLoader(context, Dispatchers.IO)

    override suspend fun extract(book: Book, fingerprint: String): PdfReflowDocument {
        val renderedPageSizes = rendererPageSizes(book)
        val document = loader.openDocument(book.uri.toUri())
        try {
            val pages = buildList {
                repeat(document.pageCount) { pageIndex ->
                    val info = document.getPageInfo(pageIndex)
                    val content = document.getPageContent(pageIndex)
                    val declaredSize = renderedPageSizes.getOrNull(pageIndex)
                    val pageWidth = declaredSize?.first ?: info.width
                    val pageHeight = declaredSize?.second ?: info.height
                    val blocks = content?.textContents.orEmpty().map { item ->
                        PdfTextBlock(
                            text = item.text,
                            bounds = item.bounds.map { rect ->
                                PdfTextBounds(rect.left, rect.top, rect.right, rect.bottom)
                            },
                        )
                    }
                    val (classification, diagnostics) = classifyPdfPage(
                        pageWidth = pageWidth,
                        pageHeight = pageHeight,
                        blocks = blocks,
                        hasImages = content?.imageContents?.isNotEmpty() == true,
                    )
                    add(
                        PdfExtractedPage(
                            index = pageIndex,
                            width = pageWidth,
                            height = pageHeight,
                            blocks = blocks,
                            classification = classification,
                            diagnostics = diagnostics,
                        )
                    )
                }
            }
            return PdfReflowDocument(book.id, book.title, fingerprint, version, pages)
        } finally {
            document.close()
        }
    }

    private fun rendererPageSizes(book: Book): List<Pair<Int, Int>> =
        context.contentResolver.openFileDescriptor(book.uri.toUri(), "r")?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                List(renderer.pageCount) { index ->
                    renderer.openPage(index).use { it.width to it.height }
                }
            }
        }.orEmpty()
}
