package com.arthur.ereader.data

import android.content.ContentResolver
import android.net.Uri
import com.arthur.ereader.core.files.FormatTools
import com.arthur.ereader.data.local.BookDao
import com.arthur.ereader.data.local.BookEntity
import com.arthur.ereader.data.local.BookmarkDao
import com.arthur.ereader.data.local.BookmarkEntity
import com.arthur.ereader.data.local.BookReaderSettingsEntity
import com.arthur.ereader.data.local.ProgressDao
import com.arthur.ereader.data.local.ProgressEntity
import com.arthur.ereader.data.local.ReaderSettingsDao
import com.arthur.ereader.data.local.ReaderSettingsEntity
import com.arthur.ereader.domain.model.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepository @Inject constructor(
    private val dao: BookDao,
    private val progressDao: ProgressDao,
    private val bookmarkDao: BookmarkDao,
    private val settingsDao: ReaderSettingsDao,
    private val resolver: ContentResolver,
    private val covers: BookCoverGenerator,
) {
    private val settingsMutex = Mutex()

    fun observe() = dao.observe().map { it.map(::toDomain) }

    fun observeWithProgress() = combine(dao.observe(), progressDao.observeAll()) { books, progress ->
        val byBook = progress.associateBy { it.bookId }
        books.map { entity ->
            val book = toDomain(entity)
            LibraryBook(book, byBook[entity.id]?.let { toProgress(it, book.format) })
        }
    }

    suspend fun import(uri: Uri): Result<Long> = runCatching {
        dao.getByUri(uri.toString())?.let { return@runCatching it.id }
        val details = queryDetails(uri)
        val format = FormatTools.detect(details.name, resolver.getType(uri))
            ?: error("Formato não suportado. Selecione EPUB, PDF ou CBZ.")
        resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val id = dao.insert(BookEntity(title = details.name.substringBeforeLast('.'), author = null, uri = uri.toString(), format = format.name, contentType = when (format) { BookFormat.CBZ -> ContentType.COMIC.name; BookFormat.PDF -> ContentType.DOCUMENT.name; BookFormat.EPUB -> ContentType.BOOK.name }, fileSize = details.size, dateAdded = System.currentTimeMillis(), lastReadAt = null, favorite = false))
        dao.get(id)?.let { entity ->
            dao.cover(id, covers.generate(toDomain(entity)).orEmpty())
        }
        id
    }

    suspend fun favorite(id: Long, value: Boolean) = dao.favorite(id, value)
    suspend fun delete(id: Long) {
        progressDao.deleteForBook(id)
        bookmarkDao.deleteForBook(id)
        dao.delete(id)
        covers.delete(id)
    }

    fun observeBookmarks(book: Book) = bookmarkDao.observe(book.id).map { entities ->
        entities.map { entity ->
            Bookmark(
                id = entity.id,
                bookId = entity.bookId,
                locator = ReaderLocator.stored(book.format, entity.locator),
                title = entity.title,
                createdAt = entity.createdAt,
            )
        }
    }

    suspend fun toggleBookmark(book: Book, locator: ReaderLocator, title: String?): Boolean {
        require(locator.format == book.format) { "A posição não pertence a este livro." }
        val existing = bookmarkDao.find(book.id, locator.payload)
        if (existing != null) {
            bookmarkDao.delete(existing.id)
            return false
        }
        bookmarkDao.insert(
            BookmarkEntity(
                bookId = book.id,
                locator = locator.payload,
                title = title,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    suspend fun backfillCovers() {
        dao.missingCovers().forEach { entity ->
            val book = toDomain(entity)
            dao.cover(book.id, covers.generate(book).orEmpty())
        }
    }
    suspend fun get(id: Long) = dao.get(id)?.let(::toDomain)
    suspend fun markRead(id: Long) = dao.read(id, System.currentTimeMillis())
    suspend fun progress(bookId: Long): ReadingProgress? {
        val book = get(bookId) ?: return null
        return progressDao.get(bookId)?.let { toProgress(it, book.format) }
    }

    /** Writes only a settled location, never intermediate pinch/drag states. */
    suspend fun saveProgress(bookId: Long, locator: ReaderLocator, percentage: Float) {
        progressDao.upsert(ProgressEntity(bookId, locator.payload, percentage.coerceIn(0f, 1f), System.currentTimeMillis()))
        markRead(bookId)
    }

    fun observeGlobalSettings() = settingsDao.observeGlobal()
        .map { it?.toDomain() ?: GlobalReaderSettings() }
        .distinctUntilChanged()

    fun observeBookOverrides(bookId: Long) = settingsDao.observeBook(bookId)
        .map { it?.toDomain() ?: BookReaderOverrides(bookId) }
        .distinctUntilChanged()

    fun observeEffectiveSettings(bookId: Long) = combine(
        observeGlobalSettings(),
        observeBookOverrides(bookId),
        ::effectiveReaderSettings,
    ).distinctUntilChanged()

    suspend fun globalSettings() = settingsDao.global()?.toDomain() ?: GlobalReaderSettings()
    suspend fun bookOverrides(bookId: Long) = settingsDao.book(bookId)?.toDomain() ?: BookReaderOverrides(bookId)
    suspend fun effectiveSettings(bookId: Long) = effectiveReaderSettings(globalSettings(), bookOverrides(bookId))

    suspend fun updateGlobalSettings(update: (GlobalReaderSettings) -> GlobalReaderSettings) = settingsMutex.withLock {
        val current = settingsDao.global()?.toDomain() ?: GlobalReaderSettings()
        settingsDao.saveGlobal(update(current).normalized().toEntity())
    }

    suspend fun updateBookOverrides(bookId: Long, update: (BookReaderOverrides) -> BookReaderOverrides) = settingsMutex.withLock {
        val current = settingsDao.book(bookId)?.toDomain() ?: BookReaderOverrides(bookId)
        settingsDao.saveBook(update(current).copy(bookId = bookId).normalized().toEntity())
    }

    suspend fun clearBookOverrides(bookId: Long) = settingsDao.clearBook(bookId)

    private fun queryDetails(uri: Uri): FileDetails = resolver.query(
        uri,
        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
        FileDetails(
            name = if (nameIndex >= 0) cursor.getString(nameIndex) ?: "Livro" else "Livro",
            size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex).coerceAtLeast(0) else 0,
        )
    } ?: FileDetails("Livro", 0)
    private fun toDomain(e: BookEntity) = Book(e.id, e.title, e.author, e.uri, BookFormat.valueOf(e.format), ContentType.valueOf(e.contentType), coverUri = e.coverUri?.takeIf(String::isNotBlank), fileSize = e.fileSize, dateAdded = e.dateAdded, lastReadAt = e.lastReadAt, favorite = e.favorite)
    private fun toProgress(e: ProgressEntity, format: BookFormat) = ReadingProgress(e.bookId, ReaderLocator.stored(format, e.locator), e.percentage, e.updatedAt)

    private fun ReaderSettingsEntity.toDomain() = GlobalReaderSettings(
        appTheme = appTheme.enumOr(AppThemeMode.SYSTEM),
        epub = EpubReaderSettings(
            fontScale = epubFontScale,
            lineHeight = epubLineHeight,
            pageMargins = epubPageMargins,
            theme = epubTheme.enumOr(EpubThemeMode.LIGHT),
            layout = epubLayout.enumOr(EpubLayoutMode.PAGED),
        ),
        pdf = PdfReaderSettings(
            pageMode = pdfPageMode.enumOr(PdfPageMode.ORIGINAL),
            fitMode = pdfFitMode.enumOr(FitMode.PAGE),
            reading = PdfReadingSettings(
                fontScale = pdfReadingFontScale,
                lineHeight = pdfReadingLineHeight,
                pageMargins = pdfReadingPageMargins,
                theme = pdfReadingTheme.enumOr(EpubThemeMode.LIGHT),
                layout = pdfReadingLayout.enumOr(EpubLayoutMode.SCROLL),
            ),
        ),
        comic = ComicReaderSettings(
            direction = comicDirection.enumOr(ReadingDirection.LTR),
            displayMode = comicDisplayMode.enumOr(ComicDisplayMode.PAGED),
            fitMode = comicFitMode.enumOr(FitMode.PAGE),
        ),
    ).normalized()

    private fun GlobalReaderSettings.toEntity() = ReaderSettingsEntity(
        epubFontScale = epub.fontScale,
        appTheme = appTheme.name,
        epubLineHeight = epub.lineHeight,
        epubPageMargins = epub.pageMargins,
        epubTheme = epub.theme.name,
        epubLayout = epub.layout.name,
        pdfPageMode = pdf.pageMode.name,
        pdfFitMode = pdf.fitMode.name,
        pdfReadingFontScale = pdf.reading.fontScale,
        pdfReadingLineHeight = pdf.reading.lineHeight,
        pdfReadingPageMargins = pdf.reading.pageMargins,
        pdfReadingTheme = pdf.reading.theme.name,
        pdfReadingLayout = pdf.reading.layout.name,
        comicDirection = comic.direction.name,
        comicDisplayMode = comic.displayMode.name,
        comicFitMode = comic.fitMode.name,
    )

    private fun BookReaderSettingsEntity.toDomain() = BookReaderOverrides(
        bookId = bookId,
        epubFontScale = epubFontScale,
        epubLineHeight = epubLineHeight,
        epubPageMargins = epubPageMargins,
        epubTheme = epubTheme?.enumOr(EpubThemeMode.LIGHT),
        epubLayout = epubLayout?.enumOr(EpubLayoutMode.PAGED),
        pdfPageMode = pdfPageMode?.enumOr(PdfPageMode.ORIGINAL),
        pdfFitMode = pdfFitMode?.enumOr(FitMode.PAGE),
        pdfZoomScale = pdfZoomScale,
        pdfReadingFontScale = pdfReadingFontScale,
        pdfReadingLineHeight = pdfReadingLineHeight,
        pdfReadingPageMargins = pdfReadingPageMargins,
        pdfReadingTheme = pdfReadingTheme?.enumOr(EpubThemeMode.LIGHT),
        pdfReadingLayout = pdfReadingLayout?.enumOr(EpubLayoutMode.SCROLL),
        comicDirection = comicDirection?.enumOr(ReadingDirection.LTR),
        comicDisplayMode = comicDisplayMode?.enumOr(ComicDisplayMode.PAGED),
        comicFitMode = comicFitMode?.enumOr(FitMode.PAGE),
    ).normalized()

    private fun BookReaderOverrides.toEntity() = BookReaderSettingsEntity(
        bookId = bookId,
        epubFontScale = epubFontScale,
        epubLineHeight = epubLineHeight,
        epubPageMargins = epubPageMargins,
        epubTheme = epubTheme?.name,
        epubLayout = epubLayout?.name,
        pdfPageMode = pdfPageMode?.name,
        pdfFitMode = pdfFitMode?.name,
        pdfZoomScale = pdfZoomScale,
        pdfReadingFontScale = pdfReadingFontScale,
        pdfReadingLineHeight = pdfReadingLineHeight,
        pdfReadingPageMargins = pdfReadingPageMargins,
        pdfReadingTheme = pdfReadingTheme?.name,
        pdfReadingLayout = pdfReadingLayout?.name,
        comicDirection = comicDirection?.name,
        comicDisplayMode = comicDisplayMode?.name,
        comicFitMode = comicFitMode?.name,
    )

    private inline fun <reified T : Enum<T>> String.enumOr(default: T): T =
        runCatching { enumValueOf<T>(this) }.getOrDefault(default)

    private data class FileDetails(val name: String, val size: Long)
}
