package com.arthur.ereader.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionDaoInstrumentedTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun close() = database.close()

    @Test
    fun collectionsAreCaseInsensitiveAndMembershipCascadesWithoutDeletingBooks() = runBlocking {
        val firstBook = database.books().insert(book("Um", "content://one"))
        val secondBook = database.books().insert(book("Dois", "content://two"))
        val now = System.currentTimeMillis()
        val collectionId = database.collections().insert(CollectionEntity(name = "Estudos", description = "Técnicos", color = "BLUE", createdAt = now, updatedAt = now))

        database.collections().replaceBooksInCollection(collectionId, setOf(firstBook, secondBook))
        assertEquals(setOf(firstBook, secondBook), database.collections().observeBookIds(collectionId).first().toSet())
        assertEquals(2, database.collections().observeAll().first().single().bookCount)

        val duplicate = runCatching {
            database.collections().insert(CollectionEntity(name = "estudos", description = "", color = "RED", createdAt = now, updatedAt = now))
        }
        assertTrue(duplicate.isFailure)

        database.books().delete(firstBook)
        assertEquals(setOf(secondBook), database.collections().observeBookIds(collectionId).first().toSet())

        database.collections().delete(collectionId)
        assertTrue(database.books().get(secondBook) != null)
    }

    @Test
    fun aBookCanBelongToMultipleCollectionsAndAssignmentsCanBeReplaced() = runBlocking {
        val bookId = database.books().insert(book("Livro", "content://book"))
        val now = System.currentTimeMillis()
        val first = database.collections().insert(CollectionEntity(name = "Favoritos pessoais", description = "", color = "RED", createdAt = now, updatedAt = now))
        val second = database.collections().insert(CollectionEntity(name = "Lendo agora", description = "", color = "GREEN", createdAt = now, updatedAt = now))

        database.collections().replaceCollectionsForBook(bookId, setOf(first, second))
        assertEquals(setOf(first, second), database.collections().observeCollectionIdsForBook(bookId).first().toSet())

        database.collections().replaceCollectionsForBook(bookId, setOf(second))
        assertEquals(setOf(second), database.collections().observeCollectionIdsForBook(bookId).first().toSet())
    }

    private fun book(title: String, uri: String) = BookEntity(
        title = title,
        author = null,
        uri = uri,
        format = "EPUB",
        contentType = "BOOK",
        fileSize = 0,
        dateAdded = System.currentTimeMillis(),
        lastReadAt = null,
        favorite = false,
    )
}
