package com.arthur.ereader.core.di
import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arthur.ereader.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
@Module @InstallIn(SingletonComponent::class) object AppModule {
    private val migration1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS reader_settings (id INTEGER NOT NULL, epubFontScale REAL NOT NULL, PRIMARY KEY(id))")
            db.execSQL("CREATE TABLE IF NOT EXISTS epub_book_settings (bookId INTEGER NOT NULL, fontScale REAL NOT NULL, PRIMARY KEY(bookId))")
        }
    }
    internal val migration2To3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE reader_settings ADD COLUMN appTheme TEXT NOT NULL DEFAULT 'SYSTEM'")
            db.execSQL("ALTER TABLE reader_settings ADD COLUMN epubLineHeight REAL NOT NULL DEFAULT 1.2")
            db.execSQL("ALTER TABLE reader_settings ADD COLUMN epubPageMargins REAL NOT NULL DEFAULT 1.0")
            db.execSQL("ALTER TABLE reader_settings ADD COLUMN epubTheme TEXT NOT NULL DEFAULT 'LIGHT'")
            db.execSQL("ALTER TABLE reader_settings ADD COLUMN epubLayout TEXT NOT NULL DEFAULT 'PAGED'")
            db.execSQL("ALTER TABLE reader_settings ADD COLUMN pdfPageMode TEXT NOT NULL DEFAULT 'ORIGINAL'")
            db.execSQL("ALTER TABLE reader_settings ADD COLUMN pdfFitMode TEXT NOT NULL DEFAULT 'PAGE'")
            db.execSQL("ALTER TABLE reader_settings ADD COLUMN comicDirection TEXT NOT NULL DEFAULT 'LTR'")
            db.execSQL("ALTER TABLE reader_settings ADD COLUMN comicDisplayMode TEXT NOT NULL DEFAULT 'PAGED'")
            db.execSQL("ALTER TABLE reader_settings ADD COLUMN comicFitMode TEXT NOT NULL DEFAULT 'PAGE'")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS book_reader_settings (
                    bookId INTEGER NOT NULL,
                    epubFontScale REAL,
                    epubLineHeight REAL,
                    epubPageMargins REAL,
                    epubTheme TEXT,
                    epubLayout TEXT,
                    pdfPageMode TEXT,
                    pdfFitMode TEXT,
                    comicDirection TEXT,
                    comicDisplayMode TEXT,
                    comicFitMode TEXT,
                    PRIMARY KEY(bookId),
                    FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent()
            )
            db.execSQL(
                "INSERT INTO book_reader_settings (bookId, epubFontScale) " +
                    "SELECT old.bookId, old.fontScale FROM epub_book_settings old " +
                    "INNER JOIN books ON books.id = old.bookId"
            )
            db.execSQL("DROP TABLE epub_book_settings")
        }
    }
    internal val migration3To4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE book_reader_settings ADD COLUMN pdfZoomScale REAL")
        }
    }
    internal val migration4To5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE reader_settings ADD COLUMN pdfReadingFontScale REAL NOT NULL DEFAULT 1.0")
            db.execSQL("ALTER TABLE reader_settings ADD COLUMN pdfReadingLineHeight REAL NOT NULL DEFAULT 1.4")
            db.execSQL("ALTER TABLE reader_settings ADD COLUMN pdfReadingPageMargins REAL NOT NULL DEFAULT 1.0")
            db.execSQL("ALTER TABLE reader_settings ADD COLUMN pdfReadingTheme TEXT NOT NULL DEFAULT 'LIGHT'")
            db.execSQL("ALTER TABLE reader_settings ADD COLUMN pdfReadingLayout TEXT NOT NULL DEFAULT 'SCROLL'")
            db.execSQL("ALTER TABLE book_reader_settings ADD COLUMN pdfReadingFontScale REAL")
            db.execSQL("ALTER TABLE book_reader_settings ADD COLUMN pdfReadingLineHeight REAL")
            db.execSQL("ALTER TABLE book_reader_settings ADD COLUMN pdfReadingPageMargins REAL")
            db.execSQL("ALTER TABLE book_reader_settings ADD COLUMN pdfReadingTheme TEXT")
            db.execSQL("ALTER TABLE book_reader_settings ADD COLUMN pdfReadingLayout TEXT")
        }
    }
    internal val migration5To6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN coverUri TEXT")
        }
    }
    internal val migration6To7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS collections (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT COLLATE NOCASE NOT NULL,
                    description TEXT NOT NULL,
                    color TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )""".trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_collections_name ON collections(name)")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS book_collection_cross_ref (
                    bookId INTEGER NOT NULL,
                    collectionId INTEGER NOT NULL,
                    PRIMARY KEY(bookId, collectionId),
                    FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(collectionId) REFERENCES collections(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_book_collection_cross_ref_bookId ON book_collection_cross_ref(bookId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_book_collection_cross_ref_collectionId ON book_collection_cross_ref(collectionId)")
        }
    }
    @Provides @Singleton fun db(@ApplicationContext c: Context) =
        Room.databaseBuilder(c, AppDatabase::class.java, "ereader.db")
            .addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6, migration6To7)
            .build()
    @Provides fun books(db: AppDatabase) = db.books()
    @Provides fun progress(db: AppDatabase) = db.progress()
    @Provides fun bookmarks(db: AppDatabase) = db.bookmarks()
    @Provides fun settings(db: AppDatabase) = db.readerSettings()
    @Provides fun collections(db: AppDatabase) = db.collections()
    @Provides fun resolver(@ApplicationContext c: Context): ContentResolver = c.contentResolver
}
