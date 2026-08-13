package com.arthur.ereader.data

import android.database.sqlite.SQLiteConstraintException
import com.arthur.ereader.data.local.CollectionDao
import com.arthur.ereader.data.local.CollectionEntity
import com.arthur.ereader.data.local.CollectionWithCount
import com.arthur.ereader.data.local.CollectionRelationEntity
import com.arthur.ereader.data.local.OrganizationDao
import com.arthur.ereader.data.local.ManualOverrideEntity
import com.arthur.ereader.domain.model.BookCollection
import com.arthur.ereader.domain.model.CollectionColor
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionRepository @Inject constructor(
    private val dao: CollectionDao,
    private val books: BookRepository,
    private val organization: OrganizationDao,
) {
    fun observeCollections() = dao.observeAll().map { items -> items.map(::toDomain) }

    fun observeCollection(id: Long) = dao.observe(id).map { it?.let(::toDomain) }

    fun observeBooks(id: Long) = combine(books.observeWithProgress(), dao.observeBookIds(id)) { source, ids ->
        val selected = ids.toSet()
        source.filter { it.book.id in selected }
    }

    fun observeCollectionIdsForBook(bookId: Long) = dao.observeCollectionIdsForBook(bookId).map(List<Long>::toSet)

    suspend fun create(name: String, description: String, color: CollectionColor): Result<Long> = runCatching {
        val values = validate(name, description)
        val now = System.currentTimeMillis()
        dao.insert(CollectionEntity(name = values.first, description = values.second, color = color.name, createdAt = now, updatedAt = now))
    }.mapConstraintError()

    suspend fun update(id: Long, name: String, description: String, color: CollectionColor): Result<Unit> = runCatching {
        require(dao.get(id) != null) { "Coleção não encontrada." }
        val values = validate(name, description)
        dao.update(id, values.first, values.second, color.name, System.currentTimeMillis())
    }.mapConstraintError()

    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun setCollectionsForBook(bookId: Long, collectionIds: Set<Long>) {
        val previous = dao.collectionIdsForBook(bookId).toSet()
        dao.replaceCollectionsForBook(bookId, collectionIds)
        organization.deleteRelationsForChild("BOOK", bookId)
        val now = System.currentTimeMillis()
        collectionIds.forEach { organization.insertRelation(CollectionRelationEntity(it, "BOOK", bookId, now)) }
        (collectionIds - previous).forEach { collectionId ->
            organization.saveOverride(ManualOverrideEntity(entityType = "BOOK", entityId = bookId, relationType = "COLLECTION", targetId = collectionId, action = "FORCE_ADD", createdAt = now))
        }
        (previous - collectionIds).forEach { collectionId ->
            organization.saveOverride(ManualOverrideEntity(entityType = "BOOK", entityId = bookId, relationType = "COLLECTION", targetId = collectionId, action = "FORCE_REMOVE", createdAt = now))
        }
    }
    suspend fun setBooksInCollection(collectionId: Long, bookIds: Set<Long>) {
        val previous = dao.bookIds(collectionId).toSet()
        dao.replaceBooksInCollection(collectionId, bookIds)
        organization.deleteChildrenOfType(collectionId, "BOOK")
        val now = System.currentTimeMillis()
        bookIds.forEach { organization.insertRelation(CollectionRelationEntity(collectionId, "BOOK", it, now)) }
        (bookIds - previous).forEach { bookId ->
            organization.saveOverride(ManualOverrideEntity(entityType = "BOOK", entityId = bookId, relationType = "COLLECTION", targetId = collectionId, action = "FORCE_ADD", createdAt = now))
        }
        (previous - bookIds).forEach { bookId ->
            organization.saveOverride(ManualOverrideEntity(entityType = "BOOK", entityId = bookId, relationType = "COLLECTION", targetId = collectionId, action = "FORCE_REMOVE", createdAt = now))
        }
    }

    private fun validate(name: String, description: String): Pair<String, String> {
        val cleanName = name.trim()
        val cleanDescription = description.trim()
        require(cleanName.isNotEmpty()) { "Informe um nome para a coleção." }
        require(cleanName.length <= 60) { "O nome deve ter no máximo 60 caracteres." }
        require(cleanDescription.length <= 240) { "A descrição deve ter no máximo 240 caracteres." }
        return cleanName to cleanDescription
    }

    private fun <T> Result<T>.mapConstraintError(): Result<T> = recoverCatching { error ->
        if (error is SQLiteConstraintException) throw IllegalArgumentException("Já existe uma coleção com esse nome.")
        throw error
    }

    private fun toDomain(item: CollectionWithCount) = BookCollection(
        id = item.id,
        name = item.name,
        description = item.description,
        color = runCatching { CollectionColor.valueOf(item.color) }.getOrDefault(CollectionColor.BLUE),
        createdAt = item.createdAt,
        updatedAt = item.updatedAt,
        bookCount = item.bookCount,
    )
}
