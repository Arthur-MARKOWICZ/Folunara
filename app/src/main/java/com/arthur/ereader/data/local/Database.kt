package com.arthur.ereader.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["fileHash"], unique = true),
        Index("seriesId"),
        Index(value = ["seriesId", "volume", "number", "publicationType"]),
    ],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String?,
    val uri: String,
    val format: String,
    val contentType: String,
    val fileSize: Long,
    val dateAdded: Long,
    val lastReadAt: Long?,
    val favorite: Boolean,
    val coverUri: String? = null,
    val fileHash: String? = null,
    val seriesId: Long? = null,
    val volume: Double? = null,
    val number: Double? = null,
    val publicationType: String = "NORMAL",
    val year: Int? = null,
    val processingStatus: String = "PENDING",
    val publisher: String? = null,
    val isbn: String? = null,
)

@Entity(tableName = "reading_progress")
data class ProgressEntity(@PrimaryKey val bookId: Long, val locator: String, val percentage: Float, val updatedAt: Long)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val bookId: Long, val locator: String, val title: String?, val createdAt: Long)

@Entity(tableName = "reader_settings")
data class ReaderSettingsEntity(
    @PrimaryKey val id: Int = GLOBAL_ID,
    val epubFontScale: Float = 1f,
    val appTheme: String = "SYSTEM",
    val epubLineHeight: Float = 1.2f,
    val epubPageMargins: Float = 1f,
    val epubTheme: String = "LIGHT",
    val epubLayout: String = "PAGED",
    val pdfPageMode: String = "ORIGINAL",
    val pdfFitMode: String = "PAGE",
    val pdfReadingFontScale: Float = 1f,
    val pdfReadingLineHeight: Float = 1.4f,
    val pdfReadingPageMargins: Float = 1f,
    val pdfReadingTheme: String = "LIGHT",
    val pdfReadingLayout: String = "SCROLL",
    val comicDirection: String = "LTR",
    val comicDisplayMode: String = "PAGED",
    val comicFitMode: String = "PAGE",
) {
    companion object { const val GLOBAL_ID = 1 }
}

@Entity(
    tableName = "book_reader_settings",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class BookReaderSettingsEntity(
    @PrimaryKey val bookId: Long,
    val epubFontScale: Float? = null,
    val epubLineHeight: Float? = null,
    val epubPageMargins: Float? = null,
    val epubTheme: String? = null,
    val epubLayout: String? = null,
    val pdfPageMode: String? = null,
    val pdfFitMode: String? = null,
    val pdfZoomScale: Float? = null,
    val pdfReadingFontScale: Float? = null,
    val pdfReadingLineHeight: Float? = null,
    val pdfReadingPageMargins: Float? = null,
    val pdfReadingTheme: String? = null,
    val pdfReadingLayout: String? = null,
    val comicDirection: String? = null,
    val comicDisplayMode: String? = null,
    val comicFitMode: String? = null,
)

@Entity(
    tableName = "collections",
    indices = [Index(value = ["name"], unique = true)],
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val description: String,
    val color: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "book_collection_cross_ref",
    primaryKeys = ["bookId", "collectionId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId"), Index("collectionId")],
)
data class BookCollectionCrossRef(val bookId: Long, val collectionId: Long)

@Entity(
    tableName = "series",
    indices = [Index(value = ["canonicalName"], unique = true)],
)
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val canonicalName: String,
    val displayName: String,
    val year: Int?,
    val publisher: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "collection_relations",
    primaryKeys = ["parentCollectionId", "childType", "childId"],
    foreignKeys = [ForeignKey(
        entity = CollectionEntity::class,
        parentColumns = ["id"],
        childColumns = ["parentCollectionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("parentCollectionId"), Index(value = ["childType", "childId"])],
)
data class CollectionRelationEntity(
    val parentCollectionId: Long,
    val childType: String,
    val childId: Long,
    val createdAt: Long,
)

@Entity(
    tableName = "manual_overrides",
    indices = [Index(value = ["entityType", "entityId", "relationType", "targetId"], unique = true)],
)
data class ManualOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityId: Long,
    val relationType: String,
    val targetId: Long,
    val action: String,
    val createdAt: Long,
)

@Entity(tableName = "import_sessions")
data class ImportSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val status: String,
    val totalItems: Int,
    val processedItems: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "import_items",
    foreignKeys = [
        ForeignKey(entity = ImportSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionId"), Index("bookId")],
)
data class ImportItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val bookId: Long,
    val status: String,
    val detectedSeries: String?,
    val confidence: Int,
    val requiresReview: Boolean,
)

@Entity(
    tableName = "pending_imports",
    foreignKeys = [ForeignKey(entity = ImportSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index(value = ["sessionId", "sourceUri"], unique = true)],
)
data class PendingImportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val sourceUri: String,
    val status: String = "PENDING",
)

@Entity(
    tableName = "organization_rules",
    indices = [Index("enabled"), Index("targetCollectionId")],
    foreignKeys = [ForeignKey(entity = CollectionEntity::class, parentColumns = ["id"], childColumns = ["targetCollectionId"], onDelete = ForeignKey.CASCADE)],
)
data class OrganizationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val field: String,
    val match: String,
    val value: String,
    val targetCollectionId: Long,
    val enabled: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "advanced_rules", indices = [Index("enabled"), Index("priority")])
data class AdvancedRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val scope: String,
    val scopeValue: String?,
    val priority: Int,
    val enabled: Boolean,
    val createdAt: Long,
)

@Entity(
    tableName = "rule_conditions",
    foreignKeys = [ForeignKey(entity = AdvancedRuleEntity::class, parentColumns = ["id"], childColumns = ["ruleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("ruleId")],
)
data class RuleConditionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val field: String,
    val match: String,
    val value: String,
)

@Entity(
    tableName = "rule_actions",
    foreignKeys = [ForeignKey(entity = AdvancedRuleEntity::class, parentColumns = ["id"], childColumns = ["ruleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("ruleId"), Index("targetCollectionId")],
)
data class RuleActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val actionType: String,
    val targetCollectionId: Long?,
    val collectionName: String?,
)

data class AdvancedRuleWithParts(
    @Embedded val rule: AdvancedRuleEntity,
    @Relation(parentColumn = "id", entityColumn = "ruleId") val conditions: List<RuleConditionEntity>,
    @Relation(parentColumn = "id", entityColumn = "ruleId") val actions: List<RuleActionEntity>,
)

data class SeriesWithCount(
    val id: Long,
    val canonicalName: String,
    val displayName: String,
    val year: Int?,
    val publisher: String?,
    val createdAt: Long,
    val bookCount: Int,
)

data class CollectionWithCount(
    val id: Long,
    val name: String,
    val description: String,
    val color: String,
    val createdAt: Long,
    val updatedAt: Long,
    val bookCount: Int,
)

@Dao interface BookDao {
    @Query("SELECT * FROM books ORDER BY dateAdded DESC") fun observe(): Flow<List<BookEntity>>
    @Query("SELECT * FROM books ORDER BY dateAdded DESC") suspend fun all(): List<BookEntity>
    @Insert suspend fun insert(book: BookEntity): Long
    @Query("UPDATE books SET favorite=:value WHERE id=:id") suspend fun favorite(id: Long, value: Boolean)
    @Query("UPDATE books SET lastReadAt=:time WHERE id=:id") suspend fun read(id: Long, time: Long)
    @Query("UPDATE books SET coverUri=:coverUri WHERE id=:id") suspend fun cover(id: Long, coverUri: String?)
    @Query("DELETE FROM books WHERE id=:id") suspend fun delete(id: Long)
    @Query("SELECT * FROM books WHERE id=:id") suspend fun get(id: Long): BookEntity?
    @Query("SELECT * FROM books WHERE uri=:uri LIMIT 1") suspend fun getByUri(uri: String): BookEntity?
    @Query("SELECT * FROM books WHERE fileHash=:hash LIMIT 1") suspend fun getByHash(hash: String): BookEntity?
    @Query("SELECT * FROM books WHERE seriesId=:seriesId ORDER BY volume, number, publicationType, title COLLATE NOCASE") fun observeBySeries(seriesId: Long): Flow<List<BookEntity>>
    @Query("SELECT * FROM books WHERE seriesId=:seriesId ORDER BY volume, number, publicationType, title COLLATE NOCASE") suspend fun bySeries(seriesId: Long): List<BookEntity>
    @Query("UPDATE books SET title=:title, fileHash=:fileHash, seriesId=:seriesId, volume=:volume, number=:number, publicationType=:publicationType, year=:year, processingStatus=:status WHERE id=:id")
    suspend fun organize(id: Long, title: String, fileHash: String?, seriesId: Long?, volume: Double?, number: Double?, publicationType: String, year: Int?, status: String)
    @Query("UPDATE books SET processingStatus=:status WHERE id=:id") suspend fun processingStatus(id: Long, status: String)
    @Query("UPDATE books SET title=:title, author=:author, contentType=:contentType, publisher=:publisher, isbn=:isbn, year=:year WHERE id=:id")
    suspend fun metadata(id: Long, title: String, author: String?, contentType: String, publisher: String?, isbn: String?, year: Int?)
    @Query("UPDATE books SET uri=:uri, fileSize=:fileSize WHERE id=:id") suspend fun updateLocation(id: Long, uri: String, fileSize: Long)
    @Query("SELECT * FROM books WHERE coverUri IS NULL") suspend fun missingCovers(): List<BookEntity>
    @Query("SELECT * FROM books WHERE processingStatus='PROCESSING'") suspend fun interruptedOrganization(): List<BookEntity>
}

@Dao
interface SeriesDao {
    @Query("""SELECT series.*, COUNT(books.id) AS bookCount FROM series LEFT JOIN books ON books.seriesId=series.id GROUP BY series.id ORDER BY series.displayName COLLATE NOCASE""")
    fun observeAll(): Flow<List<SeriesWithCount>>
    @Query("SELECT * FROM series WHERE id=:id") suspend fun get(id: Long): SeriesEntity?
    @Query("SELECT * FROM series WHERE id=:id") fun observe(id: Long): Flow<SeriesEntity?>
    @Query("SELECT * FROM series WHERE canonicalName=:canonicalName COLLATE NOCASE LIMIT 1") suspend fun find(canonicalName: String): SeriesEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: SeriesEntity): Long
    @Query("UPDATE series SET displayName=:displayName, year=:year, publisher=:publisher WHERE id=:id")
    suspend fun update(id: Long, displayName: String, year: Int?, publisher: String?)
}

@Dao
interface OrganizationDao {
    @Query("SELECT * FROM collection_relations") suspend fun relations(): List<CollectionRelationEntity>
    @Query("SELECT * FROM collection_relations") fun observeRelations(): Flow<List<CollectionRelationEntity>>
    @Query("SELECT * FROM collection_relations WHERE parentCollectionId=:parentId") fun observeChildren(parentId: Long): Flow<List<CollectionRelationEntity>>
    @Query("SELECT parentCollectionId FROM collection_relations WHERE childType=:childType AND childId=:childId") fun observeParents(childType: String, childId: Long): Flow<List<Long>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRelation(item: CollectionRelationEntity): Long
    @Query("DELETE FROM collection_relations WHERE parentCollectionId=:parentId AND childType=:childType AND childId=:childId") suspend fun deleteRelation(parentId: Long, childType: String, childId: Long)
    @Query("DELETE FROM collection_relations WHERE childType=:childType AND childId=:childId") suspend fun deleteRelationsForChild(childType: String, childId: Long)
    @Query("DELETE FROM collection_relations WHERE parentCollectionId=:parentId AND childType=:childType") suspend fun deleteChildrenOfType(parentId: Long, childType: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveOverride(item: ManualOverrideEntity): Long
    @Query("SELECT * FROM manual_overrides WHERE entityType=:entityType AND entityId=:entityId") suspend fun overrides(entityType: String, entityId: Long): List<ManualOverrideEntity>
    @Insert suspend fun createSession(item: ImportSessionEntity): Long
    @Query("UPDATE import_sessions SET status=:status, processedItems=:processed, updatedAt=:updatedAt WHERE id=:id") suspend fun updateSession(id: Long, status: String, processed: Int, updatedAt: Long)
    @Query("SELECT * FROM import_sessions WHERE status IN ('PENDING','PROCESSING','NEEDS_REVIEW') ORDER BY updatedAt DESC LIMIT 1") suspend fun resumableSession(): ImportSessionEntity?
    @Query("SELECT * FROM import_sessions WHERE id=:id") suspend fun importSession(id: Long): ImportSessionEntity?
    @Insert suspend fun insertImportItem(item: ImportItemEntity): Long
    @Query("SELECT * FROM import_items WHERE requiresReview=1 ORDER BY detectedSeries COLLATE NOCASE, id") fun observeReviewItems(): Flow<List<ImportItemEntity>>
    @Query("SELECT COUNT(*) > 0 FROM import_items WHERE sessionId=:sessionId AND requiresReview=1") suspend fun hasReviewItems(sessionId: Long): Boolean
    @Query("UPDATE import_items SET status=:status, requiresReview=:requiresReview WHERE id=:id") suspend fun updateImportItem(id: Long, status: String, requiresReview: Boolean)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPendingImports(items: List<PendingImportEntity>): List<Long>
    @Query("SELECT * FROM pending_imports WHERE sessionId=:sessionId ORDER BY id") suspend fun pendingImportsForSession(sessionId: Long): List<PendingImportEntity>
    @Query("SELECT * FROM pending_imports WHERE status IN ('PENDING','PROCESSING') ORDER BY id") suspend fun interruptedImports(): List<PendingImportEntity>
    @Query("UPDATE pending_imports SET status=:status WHERE id=:id") suspend fun updatePendingImport(id: Long, status: String)
    @Query("SELECT * FROM organization_rules ORDER BY name COLLATE NOCASE") fun observeRules(): Flow<List<OrganizationRuleEntity>>
    @Query("SELECT * FROM organization_rules WHERE enabled=1 ORDER BY id") suspend fun enabledRules(): List<OrganizationRuleEntity>
    @Insert suspend fun insertRule(item: OrganizationRuleEntity): Long
    @Query("UPDATE organization_rules SET enabled=:enabled WHERE id=:id") suspend fun setRuleEnabled(id: Long, enabled: Boolean)
    @Query("DELETE FROM organization_rules WHERE id=:id") suspend fun deleteRule(id: Long)
    @Transaction @Query("SELECT * FROM advanced_rules ORDER BY priority DESC, name COLLATE NOCASE") fun observeAdvancedRules(): Flow<List<AdvancedRuleWithParts>>
    @Transaction @Query("SELECT * FROM advanced_rules WHERE enabled=1 ORDER BY priority DESC, id") suspend fun enabledAdvancedRules(): List<AdvancedRuleWithParts>
    @Insert suspend fun insertAdvancedRule(item: AdvancedRuleEntity): Long
    @Insert suspend fun insertRuleConditions(items: List<RuleConditionEntity>)
    @Insert suspend fun insertRuleActions(items: List<RuleActionEntity>)
    @Query("UPDATE advanced_rules SET enabled=:enabled WHERE id=:id") suspend fun setAdvancedRuleEnabled(id: Long, enabled: Boolean)
    @Query("DELETE FROM advanced_rules WHERE id=:id") suspend fun deleteAdvancedRule(id: Long)

    @Transaction
    suspend fun insertAdvancedRule(rule: AdvancedRuleEntity, conditions: List<RuleConditionEntity>, actions: List<RuleActionEntity>): Long {
        val id = insertAdvancedRule(rule)
        insertRuleConditions(conditions.map { it.copy(ruleId = id) })
        insertRuleActions(actions.map { it.copy(ruleId = id) })
        return id
    }
}

@Dao interface ProgressDao {
    @Query("SELECT * FROM reading_progress WHERE bookId=:id") suspend fun get(id: Long): ProgressEntity?
    @Query("SELECT * FROM reading_progress") fun observeAll(): Flow<List<ProgressEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: ProgressEntity)
    @Query("DELETE FROM reading_progress WHERE bookId=:bookId") suspend fun deleteForBook(bookId: Long)
}

@Dao interface ReaderSettingsDao {
    @Query("SELECT * FROM reader_settings WHERE id=1") fun observeGlobal(): Flow<ReaderSettingsEntity?>
    @Query("SELECT * FROM reader_settings WHERE id=1") suspend fun global(): ReaderSettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveGlobal(item: ReaderSettingsEntity)
    @Query("SELECT * FROM book_reader_settings WHERE bookId=:bookId") fun observeBook(bookId: Long): Flow<BookReaderSettingsEntity?>
    @Query("SELECT * FROM book_reader_settings WHERE bookId=:bookId") suspend fun book(bookId: Long): BookReaderSettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveBook(item: BookReaderSettingsEntity)
    @Query("DELETE FROM book_reader_settings WHERE bookId=:bookId") suspend fun clearBook(bookId: Long)
}

@Dao interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId=:id ORDER BY createdAt DESC") fun observe(id: Long): Flow<List<BookmarkEntity>>
    @Query("SELECT * FROM bookmarks WHERE bookId=:bookId AND locator=:locator LIMIT 1") suspend fun find(bookId: Long, locator: String): BookmarkEntity?
    @Insert suspend fun insert(item: BookmarkEntity): Long
    @Query("DELETE FROM bookmarks WHERE id=:id") suspend fun delete(id: Long)
    @Query("DELETE FROM bookmarks WHERE bookId=:bookId") suspend fun deleteForBook(bookId: Long)
}

@Dao
interface CollectionDao {
    @Query(
        """SELECT collections.*, COUNT(book_collection_cross_ref.bookId) AS bookCount
           FROM collections
           LEFT JOIN book_collection_cross_ref ON collections.id = book_collection_cross_ref.collectionId
           GROUP BY collections.id
           ORDER BY collections.name COLLATE NOCASE"""
    )
    fun observeAll(): Flow<List<CollectionWithCount>>

    @Query(
        """SELECT collections.*, COUNT(book_collection_cross_ref.bookId) AS bookCount
           FROM collections
           LEFT JOIN book_collection_cross_ref ON collections.id = book_collection_cross_ref.collectionId
           WHERE collections.id=:id
           GROUP BY collections.id"""
    )
    fun observe(id: Long): Flow<CollectionWithCount?>

    @Query("SELECT * FROM collections WHERE id=:id") suspend fun get(id: Long): CollectionEntity?
    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE") suspend fun all(): List<CollectionEntity>
    @Query("SELECT * FROM collections WHERE name=:name COLLATE NOCASE LIMIT 1") suspend fun find(name: String): CollectionEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: CollectionEntity): Long
    @Query("UPDATE collections SET name=:name, description=:description, color=:color, updatedAt=:updatedAt WHERE id=:id")
    suspend fun update(id: Long, name: String, description: String, color: String, updatedAt: Long)
    @Query("DELETE FROM collections WHERE id=:id") suspend fun delete(id: Long)
    @Query("SELECT collectionId FROM book_collection_cross_ref WHERE bookId=:bookId")
    fun observeCollectionIdsForBook(bookId: Long): Flow<List<Long>>
    @Query("SELECT collectionId FROM book_collection_cross_ref WHERE bookId=:bookId") suspend fun collectionIdsForBook(bookId: Long): List<Long>
    @Query("SELECT bookId FROM book_collection_cross_ref WHERE collectionId=:collectionId")
    fun observeBookIds(collectionId: Long): Flow<List<Long>>
    @Query("SELECT bookId FROM book_collection_cross_ref WHERE collectionId=:collectionId") suspend fun bookIds(collectionId: Long): List<Long>
    @Query("DELETE FROM book_collection_cross_ref WHERE bookId=:bookId") suspend fun clearBook(bookId: Long)
    @Query("DELETE FROM book_collection_cross_ref WHERE collectionId=:collectionId") suspend fun clearCollection(collectionId: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRefs(items: List<BookCollectionCrossRef>)

    @Transaction
    suspend fun replaceCollectionsForBook(bookId: Long, collectionIds: Set<Long>) {
        clearBook(bookId)
        insertRefs(collectionIds.map { BookCollectionCrossRef(bookId, it) })
    }

    @Transaction
    suspend fun replaceBooksInCollection(collectionId: Long, bookIds: Set<Long>) {
        clearCollection(collectionId)
        insertRefs(bookIds.map { BookCollectionCrossRef(it, collectionId) })
    }
}

@Database(
    entities = [
        BookEntity::class,
        ProgressEntity::class,
        BookmarkEntity::class,
        ReaderSettingsEntity::class,
        BookReaderSettingsEntity::class,
        CollectionEntity::class,
        BookCollectionCrossRef::class,
        SeriesEntity::class,
        CollectionRelationEntity::class,
        ManualOverrideEntity::class,
        ImportSessionEntity::class,
        ImportItemEntity::class,
        PendingImportEntity::class,
        OrganizationRuleEntity::class,
        AdvancedRuleEntity::class,
        RuleConditionEntity::class,
        RuleActionEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun progress(): ProgressDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun readerSettings(): ReaderSettingsDao
    abstract fun collections(): CollectionDao
    abstract fun series(): SeriesDao
    abstract fun organization(): OrganizationDao
}
