package com.arthur.ereader.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthur.ereader.domain.model.Book
import com.arthur.ereader.data.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlin.math.sqrt

sealed interface PdfReflowState {
    data object Idle : PdfReflowState
    data object Loading : PdfReflowState
    data class Ready(val document: PdfReflowDocument) : PdfReflowState
    data class Failure(val message: String) : PdfReflowState
}

sealed interface PdfExportState {
    data object Idle : PdfExportState
    data object Exporting : PdfExportState
    data class Complete(val uri: Uri, val importedBook: Book?) : PdfExportState
    data class Failure(val message: String) : PdfExportState
}

@HiltViewModel
class PdfReflowViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PdfReflowRepository,
    private val books: BookRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<PdfReflowState>(PdfReflowState.Idle)
    val state = _state.asStateFlow()
    private val _export = MutableStateFlow<PdfExportState>(PdfExportState.Idle)
    val export = _export.asStateFlow()
    private var currentBook: Book? = null

    fun load(book: Book, force: Boolean = false) {
        if (!force && currentBook?.id == book.id && _state.value is PdfReflowState.Ready) return
        currentBook = book
        _state.value = PdfReflowState.Loading
        viewModelScope.launch {
            _state.value = repository.load(book, force).fold(
                onSuccess = { PdfReflowState.Ready(it) },
                onFailure = { PdfReflowState.Failure(it.message ?: "Não foi possível analisar o PDF.") },
            )
        }
    }

    fun export(destination: Uri) {
        val book = currentBook ?: return
        val document = (_state.value as? PdfReflowState.Ready)?.document ?: return
        _export.value = PdfExportState.Exporting
        viewModelScope.launch {
            _export.value = runCatching {
                withContext(Dispatchers.IO) {
                    val temporary = File.createTempFile("pdf-export-", ".epub", context.cacheDir)
                    try {
                        temporary.outputStream().use { output ->
                            EpubExporter().export(document, output) { page -> renderPageJpeg(book, page) }
                        }
                        EpubExporter().validate(temporary)
                        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                            temporary.inputStream().use { it.copyTo(output) }
                        } ?: error("Não foi possível criar o EPUB no destino escolhido.")
                    } finally {
                        temporary.delete()
                    }
                }
                val importedBook = books.import(destination).getOrNull()?.let { books.get(it) }
                PdfExportState.Complete(destination, importedBook)
            }.getOrElse { PdfExportState.Failure(it.message ?: "Falha ao exportar o EPUB.") }
        }
    }

    fun clearExportMessage() { _export.value = PdfExportState.Idle }

    private fun renderPageJpeg(book: Book, pageIndex: Int): ByteArray {
        return context.contentResolver.openFileDescriptor(book.uri.toUri(), "r")?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(pageIndex).use { page ->
                    val requestedWidth = page.width * EXPORT_SCALE
                    val requestedHeight = page.height * EXPORT_SCALE
                    val requestedPixels = requestedWidth.toDouble() * requestedHeight
                    val reduction = if (requestedPixels > MAX_EXPORT_IMAGE_PIXELS) {
                        sqrt(MAX_EXPORT_IMAGE_PIXELS / requestedPixels).toFloat()
                    } else 1f
                    val width = (requestedWidth * reduction).toInt().coerceAtLeast(1)
                    val height = (requestedHeight * reduction).toInt().coerceAtLeast(1)
                    // PdfRenderer requires an ARGB bitmap on some Android implementations.
                    // JPEG compression below still removes the alpha channel from the EPUB image.
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        ByteArrayOutputStream().use { bytes ->
                            check(bitmap.compress(Bitmap.CompressFormat.JPEG, EXPORT_JPEG_QUALITY, bytes))
                            bytes.toByteArray()
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        } ?: error("Não foi possível acessar o PDF.")
    }

    private companion object {
        const val EXPORT_SCALE = 2f
        const val MAX_EXPORT_IMAGE_PIXELS = 4_000_000.0
        const val EXPORT_JPEG_QUALITY = 82
    }
}
