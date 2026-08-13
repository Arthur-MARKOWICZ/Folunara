package com.arthur.ereader.domain.model

enum class BookFormat { EPUB, PDF, CBZ }
enum class ContentType { BOOK, DOCUMENT, COMIC, MANGA }
enum class PublicationType { NORMAL, ANNUAL, SPECIAL, ONE_SHOT, VOLUME }
enum class ProcessingStatus { PENDING, PROCESSING, ORGANIZED, NEEDS_REVIEW, FAILED }
enum class AutomationMode { AUTOMATIC, ASK, DISABLED }
enum class OrganizationChildType { COLLECTION, SERIES, BOOK }
enum class ManualOverrideAction { FORCE_ADD, FORCE_REMOVE }
enum class RuleField { SERIES, PUBLISHER, FORMAT, CONTENT_TYPE, AUTHOR, TITLE, ISBN, YEAR, PUBLICATION_TYPE }
enum class RuleMatch { EQUALS, NOT_EQUALS, CONTAINS, STARTS_WITH, REGEX, GREATER_OR_EQUAL, LESS_OR_EQUAL }
enum class RuleScope { LIBRARY, COMICS, MANGA, EPUB, PDF, FOLDER, IMPORT }
enum class RuleActionType { ADD_TO_COLLECTION, REMOVE_FROM_COLLECTION, CREATE_COLLECTION }

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
    val fileHash: String? = null,
    val seriesId: Long? = null,
    val volume: Double? = null,
    val number: Double? = null,
    val publicationType: PublicationType = PublicationType.NORMAL,
    val year: Int? = null,
    val processingStatus: ProcessingStatus = ProcessingStatus.PENDING,
    val publisher: String? = null,
    val isbn: String? = null,
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

data class Series(
    val id: Long = 0,
    val canonicalName: String,
    val displayName: String = canonicalName,
    val year: Int? = null,
    val publisher: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val bookCount: Int = 0,
)

data class OrganizationSuggestion(
    val bookId: Long,
    val detectedSeries: String?,
    val confidence: Int,
    val publicationType: PublicationType,
    val volume: Double?,
    val number: Double?,
    val warnings: List<String> = emptyList(),
    val requiresConfirmation: Boolean = true,
    val suggestedContentType: ContentType? = null,
    val suggestedAuthor: String? = null,
)

data class OrganizationRule(
    val id: Long = 0,
    val name: String,
    val field: RuleField,
    val match: RuleMatch = RuleMatch.EQUALS,
    val value: String,
    val targetCollectionId: Long,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

data class RuleCondition(
    val id: Long = 0,
    val field: RuleField,
    val match: RuleMatch,
    val value: String,
)

data class RuleAction(
    val id: Long = 0,
    val type: RuleActionType,
    val targetCollectionId: Long? = null,
    val collectionName: String? = null,
)

data class AdvancedOrganizationRule(
    val id: Long = 0,
    val name: String,
    val scope: RuleScope = RuleScope.LIBRARY,
    val scopeValue: String? = null,
    val priority: Int = 0,
    val enabled: Boolean = true,
    val conditions: List<RuleCondition>,
    val actions: List<RuleAction>,
    val createdAt: Long = System.currentTimeMillis(),
)

data class ExternalMetadataSuggestion(
    val providerId: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val year: Int? = null,
    val isbn: String? = null,
    val series: String? = null,
    val number: Double? = null,
    val coverUrl: String? = null,
    val sourceUrl: String? = null,
)

data class SeriesWithBooks(
    val series: Series,
    val books: List<Book>,
    val possibleMissingNumbers: List<Int> = emptyList(),
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
