package com.arthur.ereader.reader.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthur.ereader.data.BookRepository
import com.arthur.ereader.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderSettingsViewModel @Inject constructor(
    private val books: BookRepository,
) : ViewModel() {
    val globalSettings = books.observeGlobalSettings().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        GlobalReaderSettings(),
    )

    fun bookOverrides(bookId: Long): Flow<BookReaderOverrides> = books.observeBookOverrides(bookId)

    fun effectiveSettings(bookId: Long): Flow<GlobalReaderSettings> = books.observeEffectiveSettings(bookId)

    fun setAppTheme(value: AppThemeMode) = updateGlobal { copy(appTheme = value) }
    fun setGlobalEpub(value: EpubReaderSettings) = updateGlobal { copy(epub = value) }
    fun setGlobalPdf(value: PdfReaderSettings) = updateGlobal { copy(pdf = value) }
    fun setGlobalComic(value: ComicReaderSettings) = updateGlobal { copy(comic = value) }
    fun resetGlobalEpub() = setGlobalEpub(EpubReaderSettings())
    fun resetGlobalPdf() = setGlobalPdf(PdfReaderSettings())
    fun resetGlobalComic() = setGlobalComic(ComicReaderSettings())

    fun setBookEpubFont(bookId: Long, value: Float?) = updateBook(bookId) { copy(epubFontScale = value) }
    fun setBookEpubLineHeight(bookId: Long, value: Float?) = updateBook(bookId) { copy(epubLineHeight = value) }
    fun setBookEpubMargins(bookId: Long, value: Float?) = updateBook(bookId) { copy(epubPageMargins = value) }
    fun setBookEpubTheme(bookId: Long, value: EpubThemeMode?) = updateBook(bookId) { copy(epubTheme = value) }
    fun setBookEpubLayout(bookId: Long, value: EpubLayoutMode?) = updateBook(bookId) { copy(epubLayout = value) }
    fun setBookPdfPageMode(bookId: Long, value: PdfPageMode?) = updateBook(bookId) { copy(pdfPageMode = value) }
    fun setBookPdfFitMode(bookId: Long, value: FitMode?) = updateBook(bookId) { copy(pdfFitMode = value) }
    fun setBookPdfZoom(bookId: Long, value: Float?) = updateBook(bookId) { copy(pdfZoomScale = value) }
    fun setBookPdfReadingFont(bookId: Long, value: Float?) = updateBook(bookId) { copy(pdfReadingFontScale = value) }
    fun setBookPdfReadingLineHeight(bookId: Long, value: Float?) = updateBook(bookId) { copy(pdfReadingLineHeight = value) }
    fun setBookPdfReadingMargins(bookId: Long, value: Float?) = updateBook(bookId) { copy(pdfReadingPageMargins = value) }
    fun setBookPdfReadingTheme(bookId: Long, value: EpubThemeMode?) = updateBook(bookId) { copy(pdfReadingTheme = value) }
    fun setBookPdfReadingLayout(bookId: Long, value: EpubLayoutMode?) = updateBook(bookId) { copy(pdfReadingLayout = value) }
    fun setBookComicDirection(bookId: Long, value: ReadingDirection?) = updateBook(bookId) { copy(comicDirection = value) }
    fun setBookComicDisplayMode(bookId: Long, value: ComicDisplayMode?) = updateBook(bookId) { copy(comicDisplayMode = value) }
    fun setBookComicFitMode(bookId: Long, value: FitMode?) = updateBook(bookId) { copy(comicFitMode = value) }

    fun resetBookFormat(bookId: Long, format: BookFormat) = updateBook(bookId) {
        when (format) {
            BookFormat.EPUB -> copy(
                epubFontScale = null,
                epubLineHeight = null,
                epubPageMargins = null,
                epubTheme = null,
                epubLayout = null,
            )
            BookFormat.PDF -> copy(
                pdfPageMode = null,
                pdfFitMode = null,
                pdfZoomScale = null,
                pdfReadingFontScale = null,
                pdfReadingLineHeight = null,
                pdfReadingPageMargins = null,
                pdfReadingTheme = null,
                pdfReadingLayout = null,
            )
            BookFormat.CBZ -> copy(
                comicDirection = null,
                comicDisplayMode = null,
                comicFitMode = null,
            )
        }
    }

    private fun updateGlobal(update: GlobalReaderSettings.() -> GlobalReaderSettings) {
        viewModelScope.launch { books.updateGlobalSettings(update) }
    }

    private fun updateBook(bookId: Long, update: BookReaderOverrides.() -> BookReaderOverrides) {
        viewModelScope.launch { books.updateBookOverrides(bookId, update) }
    }
}
