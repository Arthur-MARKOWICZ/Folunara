package com.arthur.ereader.domain.model

enum class BookFormat { EPUB, PDF, CBZ }
enum class ContentType { BOOK, DOCUMENT, COMIC, MANGA }

data class Book(
    val id: Long = 0,
    val title: String,
    val author: String? = null,
    val uri: String,
    val format: BookFormat,
    val contentType: ContentType,
    val coverUri: String? = null,
    val fileSize: Long = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastReadAt: Long? = null,
    val favorite: Boolean = false,
)

/** A versioned, format-specific position safe to store in Room. */
data class ReaderLocator(val format: BookFormat, val payload: String, val version: Int = 1) {
    companion object {
        fun page(format: BookFormat, page: Int) = ReaderLocator(format, "{\"page\":${page.coerceAtLeast(0)}}")
        fun pdfReading(page: Int, block: Int) = ReaderLocator(
            BookFormat.PDF,
            "{\"page\":${page.coerceAtLeast(0)},\"block\":${block.coerceAtLeast(0)}}",
            version = 2,
        )

        fun stored(format: BookFormat, payload: String): ReaderLocator {
            val base = ReaderLocator(format, payload)
            return if (format == BookFormat.PDF && base.blockOrNull() != null) base.copy(version = 2) else base
        }
    }
    fun pageOrNull() = Regex("\\\"page\\\"\\s*:\\s*(\\d+)").find(payload)?.groupValues?.getOrNull(1)?.toIntOrNull()
    fun blockOrNull() = Regex("\\\"block\\\"\\s*:\\s*(\\d+)").find(payload)?.groupValues?.getOrNull(1)?.toIntOrNull()

    fun samePositionAs(other: ReaderLocator): Boolean =
        format == other.format && version == other.version && payload == other.payload
}

data class ReadingProgress(
    val bookId: Long,
    val locator: ReaderLocator,
    val percentage: Float,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class LibraryBook(val book: Book, val progress: ReadingProgress?)

enum class CollectionColor { RED, ORANGE, AMBER, GREEN, TEAL, BLUE, INDIGO, PURPLE }

data class BookCollection(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val color: CollectionColor = CollectionColor.BLUE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val bookCount: Int = 0,
)

data class Bookmark(val id: Long = 0, val bookId: Long, val locator: ReaderLocator, val title: String? = null, val createdAt: Long = System.currentTimeMillis())

enum class AppThemeMode { SYSTEM, LIGHT, DARK }
enum class EpubThemeMode { LIGHT, SEPIA, DARK }
enum class EpubLayoutMode { PAGED, SCROLL }
enum class PdfPageMode { ORIGINAL, CONTENT_FIT, READING }
enum class FitMode { PAGE, WIDTH, HEIGHT }
enum class ReadingDirection { LTR, RTL }
enum class ComicDisplayMode { PAGED, VERTICAL }
enum class LibraryLayoutMode { GRID, LIST }
enum class LibrarySortMode { RECENTLY_ADDED, LAST_READ, TITLE, AUTHOR, PROGRESS }

data class EpubReaderSettings(
    val fontScale: Float = 1f,
    val lineHeight: Float = 1.2f,
    val pageMargins: Float = 1f,
    val theme: EpubThemeMode = EpubThemeMode.LIGHT,
    val layout: EpubLayoutMode = EpubLayoutMode.PAGED,
)

data class PdfReaderSettings(
    val pageMode: PdfPageMode = PdfPageMode.ORIGINAL,
    val fitMode: FitMode = FitMode.PAGE,
    val reading: PdfReadingSettings = PdfReadingSettings(),
)

data class PdfReadingSettings(
    val fontScale: Float = 1f,
    val lineHeight: Float = 1.4f,
    val pageMargins: Float = 1f,
    val theme: EpubThemeMode = EpubThemeMode.LIGHT,
    val layout: EpubLayoutMode = EpubLayoutMode.SCROLL,
)

data class ComicReaderSettings(
    val direction: ReadingDirection = ReadingDirection.LTR,
    val displayMode: ComicDisplayMode = ComicDisplayMode.PAGED,
    val fitMode: FitMode = FitMode.PAGE,
)

data class GlobalReaderSettings(
    val appTheme: AppThemeMode = AppThemeMode.SYSTEM,
    val epub: EpubReaderSettings = EpubReaderSettings(),
    val pdf: PdfReaderSettings = PdfReaderSettings(),
    val comic: ComicReaderSettings = ComicReaderSettings(),
)

/** Nullable fields inherit their value from [GlobalReaderSettings]. */
data class BookReaderOverrides(
    val bookId: Long,
    val epubFontScale: Float? = null,
    val epubLineHeight: Float? = null,
    val epubPageMargins: Float? = null,
    val epubTheme: EpubThemeMode? = null,
    val epubLayout: EpubLayoutMode? = null,
    val pdfPageMode: PdfPageMode? = null,
    val pdfFitMode: FitMode? = null,
    val pdfZoomScale: Float? = null,
    val pdfReadingFontScale: Float? = null,
    val pdfReadingLineHeight: Float? = null,
    val pdfReadingPageMargins: Float? = null,
    val pdfReadingTheme: EpubThemeMode? = null,
    val pdfReadingLayout: EpubLayoutMode? = null,
    val comicDirection: ReadingDirection? = null,
    val comicDisplayMode: ComicDisplayMode? = null,
    val comicFitMode: FitMode? = null,
)

fun GlobalReaderSettings.normalized() = copy(
    epub = epub.copy(
        fontScale = epub.fontScale.coerceIn(0.7f, 2f),
        lineHeight = epub.lineHeight.coerceIn(1f, 2f),
        pageMargins = epub.pageMargins.coerceIn(0f, 4f),
    ),
    pdf = pdf.copy(
        reading = pdf.reading.copy(
            fontScale = pdf.reading.fontScale.coerceIn(0.7f, 2f),
            lineHeight = pdf.reading.lineHeight.coerceIn(1f, 2f),
            pageMargins = pdf.reading.pageMargins.coerceIn(0f, 4f),
        ),
    ),
)

fun BookReaderOverrides.normalized() = copy(
    epubFontScale = epubFontScale?.coerceIn(0.7f, 2f),
    epubLineHeight = epubLineHeight?.coerceIn(1f, 2f),
    epubPageMargins = epubPageMargins?.coerceIn(0f, 4f),
    pdfZoomScale = pdfZoomScale?.coerceIn(1f, 4f),
    pdfReadingFontScale = pdfReadingFontScale?.coerceIn(0.7f, 2f),
    pdfReadingLineHeight = pdfReadingLineHeight?.coerceIn(1f, 2f),
    pdfReadingPageMargins = pdfReadingPageMargins?.coerceIn(0f, 4f),
)

fun effectiveReaderSettings(
    global: GlobalReaderSettings,
    overrides: BookReaderOverrides?,
): GlobalReaderSettings {
    val defaults = global.normalized()
    val book = overrides?.normalized() ?: return defaults
    return defaults.copy(
        epub = defaults.epub.copy(
            fontScale = book.epubFontScale ?: defaults.epub.fontScale,
            lineHeight = book.epubLineHeight ?: defaults.epub.lineHeight,
            pageMargins = book.epubPageMargins ?: defaults.epub.pageMargins,
            theme = book.epubTheme ?: defaults.epub.theme,
            layout = book.epubLayout ?: defaults.epub.layout,
        ),
        pdf = defaults.pdf.copy(
            pageMode = book.pdfPageMode ?: defaults.pdf.pageMode,
            fitMode = book.pdfFitMode ?: defaults.pdf.fitMode,
            reading = defaults.pdf.reading.copy(
                fontScale = book.pdfReadingFontScale ?: defaults.pdf.reading.fontScale,
                lineHeight = book.pdfReadingLineHeight ?: defaults.pdf.reading.lineHeight,
                pageMargins = book.pdfReadingPageMargins ?: defaults.pdf.reading.pageMargins,
                theme = book.pdfReadingTheme ?: defaults.pdf.reading.theme,
                layout = book.pdfReadingLayout ?: defaults.pdf.reading.layout,
            ),
        ),
        comic = defaults.comic.copy(
            direction = book.comicDirection ?: defaults.comic.direction,
            displayMode = book.comicDisplayMode ?: defaults.comic.displayMode,
            fitMode = book.comicFitMode ?: defaults.comic.fitMode,
        ),
    )
}

fun progressForPage(page: Int, totalPages: Int): Float =
    if (totalPages <= 0) 0f else ((page.coerceIn(0, totalPages - 1) + 1).toFloat() / totalPages).coerceIn(0f, 1f)

fun effectiveEpubFontScale(globalScale: Float, overrideScale: Float?): Float =
    (overrideScale ?: globalScale).coerceIn(0.7f, 2.0f)
