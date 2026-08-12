package com.arthur.ereader.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
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

@Entity(tableName = "books")
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
    @Insert suspend fun insert(book: BookEntity): Long
    @Query("UPDATE books SET favorite=:value WHERE id=:id") suspend fun favorite(id: Long, value: Boolean)
    @Query("UPDATE books SET lastReadAt=:time WHERE id=:id") suspend fun read(id: Long, time: Long)
    @Query("UPDATE books SET coverUri=:coverUri WHERE id=:id") suspend fun cover(id: Long, coverUri: String?)
    @Query("DELETE FROM books WHERE id=:id") suspend fun delete(id: Long)
    @Query("SELECT * FROM books WHERE id=:id") suspend fun get(id: Long): BookEntity?
    @Query("SELECT * FROM books WHERE uri=:uri LIMIT 1") suspend fun getByUri(uri: String): BookEntity?
    @Query("SELECT * FROM books WHERE coverUri IS NULL") suspend fun missingCovers(): List<BookEntity>
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
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: CollectionEntity): Long
    @Query("UPDATE collections SET name=:name, description=:description, color=:color, updatedAt=:updatedAt WHERE id=:id")
    suspend fun update(id: Long, name: String, description: String, color: String, updatedAt: Long)
    @Query("DELETE FROM collections WHERE id=:id") suspend fun delete(id: Long)
    @Query("SELECT collectionId FROM book_collection_cross_ref WHERE bookId=:bookId")
    fun observeCollectionIdsForBook(bookId: Long): Flow<List<Long>>
    @Query("SELECT bookId FROM book_collection_cross_ref WHERE collectionId=:collectionId")
    fun observeBookIds(collectionId: Long): Flow<List<Long>>
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
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun progress(): ProgressDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun readerSettings(): ReaderSettingsDao
    abstract fun collections(): CollectionDao
}
