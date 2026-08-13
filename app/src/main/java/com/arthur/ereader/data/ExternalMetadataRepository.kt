package com.arthur.ereader.data

import com.arthur.ereader.data.external.OpenLibraryMetadataService
import com.arthur.ereader.data.local.BookDao
import com.arthur.ereader.data.local.ManualOverrideEntity
import com.arthur.ereader.data.local.OrganizationDao
import com.arthur.ereader.data.local.SeriesDao
import com.arthur.ereader.data.local.SeriesEntity
import com.arthur.ereader.domain.model.ExternalMetadataSuggestion
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalMetadataRepository @Inject constructor(
    private val books: BookDao,
    private val series: SeriesDao,
    private val organization: OrganizationDao,
    private val preferences: LibraryPreferences,
    private val service: OpenLibraryMetadataService,
    private val covers: BookCoverGenerator,
) {
    val enabled = preferences.externalMetadataEnabled

    suspend fun setEnabled(value: Boolean) = preferences.setExternalMetadataEnabled(value)

    suspend fun search(bookId: Long): Result<List<ExternalMetadataSuggestion>> = runCatching {
        require(preferences.externalMetadataEnabled.first()) { "Autorize as consultas externas antes de pesquisar." }
        val book = requireNotNull(books.get(bookId)) { "Item não encontrado." }
        service.search(book)
    }

    suspend fun apply(bookId: Long, suggestion: ExternalMetadataSuggestion, includeCover: Boolean): Result<Unit> = runCatching {
        require(preferences.externalMetadataEnabled.first()) { "As consultas externas estão desativadas." }
        val book = requireNotNull(books.get(bookId)) { "Item não encontrado." }
        books.metadata(
            id = bookId,
            title = suggestion.title.trim().ifBlank { book.title },
            author = suggestion.authors.joinToString().takeIf(String::isNotBlank) ?: book.author,
            contentType = book.contentType,
            publisher = suggestion.publisher ?: book.publisher,
            isbn = suggestion.isbn ?: book.isbn,
            year = suggestion.year ?: book.year,
        )
        suggestion.series?.trim()?.takeIf(String::isNotBlank)?.let { seriesName ->
            val seriesId = series.find(seriesName)?.id ?: series.insert(
                SeriesEntity(canonicalName = seriesName, displayName = seriesName, year = suggestion.year, publisher = suggestion.publisher, createdAt = System.currentTimeMillis()),
            )
            books.organize(bookId, suggestion.title, book.fileHash, seriesId, book.volume, suggestion.number ?: book.number, book.publicationType, suggestion.year ?: book.year, book.processingStatus)
            organization.saveOverride(
                ManualOverrideEntity(entityType = "BOOK", entityId = bookId, relationType = "SERIES", targetId = seriesId, action = "FORCE_ADD", createdAt = System.currentTimeMillis()),
            )
        }
        if (includeCover) suggestion.coverUrl?.let { url ->
            covers.saveExternal(bookId, service.downloadCover(url))?.let { books.cover(bookId, it) }
        }
    }
}
