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
import com.arthur.ereader.data.metadata.ComicInfoParser
import com.arthur.ereader.data.metadata.EpubMetadataParser
import com.arthur.ereader.data.metadata.PdfMetadataParser
import com.arthur.ereader.domain.model.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
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
    private val cbrConverter: CbrConverter,
    private val organization: OrganizationRepository,
    private val preferences: LibraryPreferences,
) {
    private val settingsMutex = Mutex()
    private val importMutex = Mutex()
    private val _duplicateCandidates = MutableStateFlow<List<DuplicateCandidate>>(emptyList())
    val duplicateCandidates = _duplicateCandidates.asStateFlow()

    fun observe() = dao.observe().map { it.map(::toDomain) }

    fun observeWithProgress() = combine(dao.observe(), progressDao.observeAll()) { books, progress ->
        val byBook = progress.associateBy { it.bookId }
        books.map { entity ->
            val book = toDomain(entity)
            LibraryBook(book, byBook[entity.id]?.let { toProgress(it, book.format) })
        }
    }

    suspend fun import(uri: Uri): Result<Long> {
        val sessionId = organization.startImport(1)
        persistUriAccess(listOf(uri))
        val pending = organization.registerPendingImports(sessionId, listOf(uri.toString())).single()
        return try {
            organization.updatePendingImport(pending.id, "PROCESSING")
            val imported = importMutex.withLock { withContext(Dispatchers.IO) { importLocked(uri, sessionId) } }
            organization.updatePendingImport(pending.id, "COMPLETED")
            organization.finishImport(sessionId, 1, imported.needsReview)
            Result.success(imported.id)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            organization.updatePendingImport(pending.id, "FAILED")
            organization.failImport(sessionId, 1)
            Result.failure(error)
        }
    }

    suspend fun importAll(uris: List<Uri>): BatchImportResult {
        val uniqueUris = uris.distinctBy(Uri::toString)
        if (uniqueUris.isEmpty()) return BatchImportResult(0, 0, emptyList())
        val sessionId = organization.startImport(uniqueUris.size)
        persistUriAccess(uniqueUris)
        val pendingImports = organization.registerPendingImports(sessionId, uniqueUris.map(Uri::toString))
        var imported = 0
        var duplicates = 0
        var needsReview = false
        val failures = mutableListOf<String>()
        uniqueUris.forEachIndexed { index, uri ->
            val pending = pendingImports.first { it.sourceUri == uri.toString() }
            try {
                organization.updatePendingImport(pending.id, "PROCESSING")
                val result = importMutex.withLock { withContext(Dispatchers.IO) { importLocked(uri, sessionId) } }
                imported++
                needsReview = needsReview || result.needsReview
                organization.updatePendingImport(pending.id, "COMPLETED")
            } catch (error: DuplicateContentException) {
                duplicates++
                organization.updatePendingImport(pending.id, "COMPLETED")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += error.message ?: "Não foi possível importar este arquivo."
                organization.updatePendingImport(pending.id, "FAILED")
            }
            organization.updateImportProgress(sessionId, index + 1)
        }
        organization.finishImport(sessionId, uniqueUris.size, needsReview)
        return BatchImportResult(total = uniqueUris.size, imported = imported, failures = failures, duplicates = duplicates)
    }

    private suspend fun importLocked(uri: Uri, sessionId: Long): ImportedBook {
        dao.getByUri(uri.toString())?.let { return ImportedBook(it.id, it.processingStatus == ProcessingStatus.NEEDS_REVIEW.name) }
        val details = queryDetails(uri)
        val mimeType = resolver.getType(uri)
        val isCbr = FormatTools.isCbr(details.name, mimeType)
        val format = FormatTools.detect(details.name, mimeType)
            ?: if (isCbr) BookFormat.CBZ else error("Formato não suportado. Selecione EPUB, PDF, CBZ ou CBR.")

        val storedUri: Uri
        val storedSize: Long
        var converted = false
        if (isCbr) {
            val destination = cbrConverter.destinationUri(uri, details.name)
            dao.getByUri(destination.toString())?.let { return ImportedBook(it.id, it.processingStatus == ProcessingStatus.NEEDS_REVIEW.name) }
            val comic = cbrConverter.convert(uri, details.name)
            storedUri = comic.uri
            storedSize = comic.size
            converted = true
        } else {
            resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            storedUri = uri
            storedSize = details.size
        }

        val fileHash = sha256(storedUri)
        dao.getByHash(fileHash)?.let { existing ->
            if (existing.uri == storedUri.toString()) return ImportedBook(existing.id, existing.processingStatus == ProcessingStatus.NEEDS_REVIEW.name)
            val oldLocationStillReadable = runCatching {
                resolver.openInputStream(Uri.parse(existing.uri))?.use { it.read() } != null
            }.getOrDefault(false)
            if (!oldLocationStillReadable) {
                dao.updateLocation(existing.id, storedUri.toString(), storedSize)
                return ImportedBook(existing.id, existing.processingStatus == ProcessingStatus.NEEDS_REVIEW.name)
            }
            val candidate = DuplicateCandidate(existing.id, storedUri.toString(), storedSize, converted)
            _duplicateCandidates.update { current -> (current + candidate).distinctBy(DuplicateCandidate::existingBookId) }
            throw DuplicateContentException(existing.id)
        }
        val metadata = extractMetadata(format, storedUri)

        val id = try {
            dao.insert(BookEntity(title = details.name.substringBeforeLast('.'), author = null, uri = storedUri.toString(), format = format.name, contentType = when (format) { BookFormat.CBZ -> ContentType.COMIC.name; BookFormat.PDF -> ContentType.DOCUMENT.name; BookFormat.EPUB -> ContentType.BOOK.name }, fileSize = storedSize, dateAdded = System.currentTimeMillis(), lastReadAt = null, favorite = false, fileHash = fileHash))
        } catch (error: Exception) {
            if (converted) cbrConverter.deleteConverted(storedUri.toString())
            throw error
        }
        dao.get(id)?.let { entity ->
            dao.cover(id, covers.generate(toDomain(entity)).orEmpty())
        }
        val suggestion = organization.processImported(
            sessionId,
            requireNotNull(dao.get(id)),
            details.name,
            metadata,
            fileHash,
            preferences.automationMode.first(),
        )
        return ImportedBook(id, suggestion.requiresConfirmation)
    }

    suspend fun favorite(id: Long, value: Boolean) = dao.favorite(id, value)
    suspend fun confirmDuplicate(candidate: DuplicateCandidate) {
        val existing = dao.get(candidate.existingBookId) ?: return cancelDuplicate(candidate)
        dao.updateLocation(existing.id, candidate.replacementUri, candidate.fileSize)
        if (existing.uri != candidate.replacementUri) cbrConverter.deleteConverted(existing.uri)
        _duplicateCandidates.update { it - candidate }
    }

    fun cancelDuplicate(candidate: DuplicateCandidate) {
        if (candidate.managedConversion) cbrConverter.deleteConverted(candidate.replacementUri)
        _duplicateCandidates.update { it - candidate }
    }
    suspend fun delete(id: Long) {
        val storedUri = get(id)?.uri
        progressDao.deleteForBook(id)
        bookmarkDao.deleteForBook(id)
        dao.delete(id)
        covers.delete(id)
        storedUri?.let(cbrConverter::deleteConverted)
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
    suspend fun resumeInterruptedOrganization() = withContext(Dispatchers.IO) {
        val pending = dao.interruptedOrganization()
        if (pending.isEmpty()) return@withContext
        val mode = preferences.automationMode.first()
        if (mode == AutomationMode.DISABLED) return@withContext
        val sessionId = organization.resumableImport()?.id ?: organization.startImport(pending.size)
        var needsReview = false
        pending.forEach { book ->
            val uri = Uri.parse(book.uri)
            val metadata = extractMetadata(runCatching { BookFormat.valueOf(book.format) }.getOrDefault(BookFormat.PDF), uri)
            val suggestion = organization.processImported(
                sessionId = sessionId,
                book = book,
                fileName = book.title + "." + book.format.lowercase(),
                metadata = metadata,
                fileHash = book.fileHash ?: sha256(uri),
                automationMode = mode,
            )
            needsReview = needsReview || suggestion.requiresConfirmation
        }
        organization.finishImport(sessionId, pending.size, needsReview)
    }
    suspend fun resumeInterruptedImports() = withContext(Dispatchers.IO) {
        val interrupted = organization.interruptedImports()
        interrupted.groupBy { it.sessionId }.forEach { (sessionId, items) ->
            val session = organization.importSession(sessionId)
            val total = session?.totalItems ?: items.size
            val alreadyProcessed = (total - items.size).coerceAtLeast(0)
            var needsReview = false
            items.forEachIndexed { index, pending ->
                organization.updatePendingImport(pending.id, "PROCESSING")
                try {
                    val result = importMutex.withLock { importLocked(Uri.parse(pending.sourceUri), sessionId) }
                    needsReview = needsReview || result.needsReview
                    organization.updatePendingImport(pending.id, "COMPLETED")
                } catch (error: CancellationException) {
                    throw error
                } catch (_: DuplicateContentException) {
                    organization.updatePendingImport(pending.id, "COMPLETED")
                } catch (_: Exception) {
                    organization.updatePendingImport(pending.id, "FAILED")
                }
                organization.updateImportProgress(sessionId, alreadyProcessed + index + 1)
            }
            organization.finishImport(sessionId, total, needsReview || organization.hasReviewItems(sessionId))
        }
    }
    suspend fun reprocessBook(bookId: Long): Result<OrganizationSuggestion> = runCatching {
        withContext(Dispatchers.IO) {
            val book = requireNotNull(dao.get(bookId)) { "Livro não encontrado." }
            val format = runCatching { BookFormat.valueOf(book.format) }.getOrDefault(BookFormat.PDF)
            val uri = Uri.parse(book.uri)
            val metadata = extractMetadata(format, uri)
            val fileName = queryDetails(uri).name.takeUnless { it == "Livro" } ?: "${book.title}.${format.name.lowercase()}"
            organization.reprocess(bookId, fileName, metadata)
        }
    }

    suspend fun reprocessSeries(seriesId: Long): BatchReprocessResult = reprocessIds(organization.seriesBookIds(seriesId))

    suspend fun reprocessLibrary(): BatchReprocessResult = reprocessIds(organization.allBookIds())

    private suspend fun reprocessIds(ids: List<Long>): BatchReprocessResult {
        var reviewed = 0
        val failures = mutableListOf<String>()
        ids.forEach { id ->
            reprocessBook(id).fold(
                onSuccess = { if (it.requiresConfirmation) reviewed++ },
                onFailure = { failures += it.message ?: "Falha ao reprocessar item $id." },
            )
        }
        return BatchReprocessResult(ids.size, reviewed, failures)
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
    private fun toDomain(e: BookEntity) = Book(
        e.id, e.title, e.author, e.uri, BookFormat.valueOf(e.format), ContentType.valueOf(e.contentType),
        coverUri = e.coverUri?.takeIf(String::isNotBlank), fileSize = e.fileSize, dateAdded = e.dateAdded,
        lastReadAt = e.lastReadAt, favorite = e.favorite, fileHash = e.fileHash, seriesId = e.seriesId,
        volume = e.volume, number = e.number, publicationType = e.publicationType.enumOr(PublicationType.NORMAL),
        year = e.year, processingStatus = e.processingStatus.enumOr(ProcessingStatus.PENDING), publisher = e.publisher, isbn = e.isbn,
    )
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
    private data class ImportedBook(val id: Long, val needsReview: Boolean)

    private fun sha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)?.buffered()?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        } ?: throw IOException("Não foi possível ler o arquivo selecionado.")
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun extractMetadata(format: BookFormat, uri: Uri) = when (format) {
        BookFormat.CBZ -> resolver.openInputStream(uri)?.use(ComicInfoParser::parse)
        BookFormat.EPUB -> resolver.openInputStream(uri)?.use(EpubMetadataParser::parse)
        BookFormat.PDF -> resolver.openInputStream(uri)?.use(PdfMetadataParser::parse)
    }

    private suspend fun persistUriAccess(uris: List<Uri>) = withContext(Dispatchers.IO) {
        uris.filter { it.scheme == ContentResolver.SCHEME_CONTENT }.forEach { uri ->
            runCatching { resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }
}

class DuplicateContentException(val existingBookId: Long) : IOException(
    "Este conteúdo já existe na biblioteca. Confirme ou cancele a substituição.",
)

data class DuplicateCandidate(
    val existingBookId: Long,
    val replacementUri: String,
    val fileSize: Long,
    val managedConversion: Boolean,
)

data class BatchReprocessResult(val total: Int, val needsReview: Int, val failures: List<String>) {
    fun message() = "$total itens reprocessados; $needsReview precisam de revisão${if (failures.isEmpty()) "." else "; ${failures.size} falharam."}"
}

data class BatchImportResult(
    val total: Int,
    val imported: Int,
    val failures: List<String>,
    val duplicates: Int = 0,
) {
    fun message(): String = when {
        total == 0 -> "Nenhum arquivo foi selecionado."
        duplicates > 0 && imported == 0 && failures.isEmpty() -> "$duplicates ${if (duplicates == 1) "duplicata encontrada" else "duplicatas encontradas"}; confirme a substituição na organização."
        failures.isEmpty() && imported == 1 -> "Arquivo importado e adicionado à biblioteca.${if (duplicates > 0) " $duplicates duplicata ignorada." else ""}"
        failures.isEmpty() -> "$imported arquivos importados e adicionados à biblioteca.${if (duplicates > 0) " $duplicates duplicatas ignoradas." else ""}"
        imported == 0 && total == 1 -> failures.first()
        imported == 0 -> "Nenhum dos $total arquivos pôde ser importado. ${failures.first()}"
        else -> "$imported de $total arquivos foram importados; ${failures.size} falharam. ${failures.first()}"
    }
}
