package com.arthur.ereader.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arthur.ereader.data.OrganizationRepository
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

    @Test
    fun hierarchySupportsMultipleParentsAndSeriesBooksKeepVolumeNumberOrder() = runBlocking {
        val now = System.currentTimeMillis()
        val batman = database.collections().insert(CollectionEntity(name = "Batman", description = "", color = "BLUE", createdAt = now, updatedAt = now))
        val universe = database.collections().insert(CollectionEntity(name = "Absolute Universe", description = "", color = "PURPLE", createdAt = now, updatedAt = now))
        val child = database.collections().insert(CollectionEntity(name = "Gotham", description = "", color = "INDIGO", createdAt = now, updatedAt = now))
        database.organization().insertRelation(CollectionRelationEntity(batman, "COLLECTION", child, now))
        database.organization().insertRelation(CollectionRelationEntity(universe, "COLLECTION", child, now))
        assertEquals(setOf(batman, universe), database.organization().observeParents("COLLECTION", child).first().toSet())

        val seriesId = database.series().insert(SeriesEntity(canonicalName = "Absolute Batman", displayName = "Absolute Batman (2024)", year = 2024, publisher = "DC", createdAt = now))
        val issueTwo = database.books().insert(book("#2", "content://two").copy(seriesId = seriesId, volume = 1.0, number = 2.0))
        val issueOne = database.books().insert(book("#1", "content://one").copy(seriesId = seriesId, volume = 1.0, number = 1.0))
        assertEquals(listOf(issueOne, issueTwo), database.books().bySeries(seriesId).map { it.id })
    }

    @Test
    fun simpleRulesArePersistedAndRemovedWithTargetCollection() = runBlocking {
        val now = System.currentTimeMillis()
        val collectionId = database.collections().insert(CollectionEntity(name = "DC Comics", description = "", color = "BLUE", createdAt = now, updatedAt = now))
        database.organization().insertRule(OrganizationRuleEntity(name = "Editora DC", field = "PUBLISHER", match = "EQUALS", value = "DC", targetCollectionId = collectionId, enabled = true, createdAt = now))
        assertEquals(1, database.organization().enabledRules().size)
        database.collections().delete(collectionId)
        assertTrue(database.organization().enabledRules().isEmpty())
    }

    @Test
    fun advancedRulesPersistMultipleConditionsAndActionsAndCascadeTheirParts() = runBlocking {
        val now = System.currentTimeMillis()
        val collectionId = database.collections().insert(CollectionEntity(name = "Clássicos", description = "", color = "BLUE", createdAt = now, updatedAt = now))
        val ruleId = database.organization().insertAdvancedRule(
            AdvancedRuleEntity(name = "Clássicos PDF", scope = "PDF", scopeValue = null, priority = 20, enabled = true, createdAt = now),
            listOf(
                RuleConditionEntity(ruleId = 0, field = "FORMAT", match = "EQUALS", value = "PDF"),
                RuleConditionEntity(ruleId = 0, field = "YEAR", match = "LESS_OR_EQUAL", value = "1999"),
            ),
            listOf(
                RuleActionEntity(ruleId = 0, actionType = "ADD_TO_COLLECTION", targetCollectionId = collectionId, collectionName = null),
                RuleActionEntity(ruleId = 0, actionType = "CREATE_COLLECTION", targetCollectionId = null, collectionName = "Arquivo"),
            ),
        )
        val stored = database.organization().enabledAdvancedRules().single()
        assertEquals(2, stored.conditions.size)
        assertEquals(2, stored.actions.size)
        database.organization().deleteAdvancedRule(ruleId)
        assertTrue(database.organization().enabledAdvancedRules().isEmpty())
    }

    @Test
    fun booksAndSeriesCanBeAssignedManuallyAndOverridesArePreserved() = runBlocking {
        val now = System.currentTimeMillis()
        val previousSeries = database.series().insert(SeriesEntity(canonicalName = "Anterior", displayName = "Anterior", year = null, publisher = null, createdAt = now))
        val targetSeries = database.series().insert(SeriesEntity(canonicalName = "Destino", displayName = "Destino", year = null, publisher = null, createdAt = now))
        val bookId = database.books().insert(book("Livro", "content://manual").copy(seriesId = previousSeries))
        val collectionId = database.collections().insert(CollectionEntity(name = "Coleção manual", description = "", color = "BLUE", createdAt = now, updatedAt = now))
        val repository = OrganizationRepository(database.books(), database.series(), database.collections(), database.organization())

        repository.addBooksToSeries(targetSeries, setOf(bookId)).getOrThrow()
        assertEquals(targetSeries, database.books().get(bookId)?.seriesId)
        assertTrue(database.organization().overrides("BOOK", bookId).any { it.targetId == previousSeries && it.action == "FORCE_REMOVE" })
        assertTrue(database.organization().overrides("BOOK", bookId).any { it.targetId == targetSeries && it.action == "FORCE_ADD" })

        repository.setSeriesInCollection(collectionId, setOf(targetSeries)).getOrThrow()
        assertEquals(setOf(collectionId), database.organization().observeParents("SERIES", targetSeries).first().toSet())
        assertTrue(database.organization().overrides("SERIES", targetSeries).any { it.targetId == collectionId && it.action == "FORCE_ADD" })

        repository.setSeriesInCollection(collectionId, emptySet()).getOrThrow()
        assertTrue(database.organization().observeParents("SERIES", targetSeries).first().isEmpty())
        assertTrue(database.organization().overrides("SERIES", targetSeries).any { it.targetId == collectionId && it.action == "FORCE_REMOVE" })
    }

    @Test
    fun seriesCanBeCreatedManuallyWithNormalizedUniqueName() = runBlocking {
        val repository = OrganizationRepository(database.books(), database.series(), database.collections(), database.organization())
        val seriesId = repository.createSeries("  Nova   Série  ", 2026, "  Editora  ").getOrThrow()

        val stored = database.series().get(seriesId)
        assertEquals("Nova Série", stored?.canonicalName)
        assertEquals("Nova Série", stored?.displayName)
        assertEquals(2026, stored?.year)
        assertEquals("Editora", stored?.publisher)
        assertTrue(repository.createSeries("nova série", null, null).isFailure)
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
