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
    internal val migration7To8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN fileHash TEXT")
            db.execSQL("ALTER TABLE books ADD COLUMN seriesId INTEGER")
            db.execSQL("ALTER TABLE books ADD COLUMN volume REAL")
            db.execSQL("ALTER TABLE books ADD COLUMN number REAL")
            db.execSQL("ALTER TABLE books ADD COLUMN publicationType TEXT NOT NULL DEFAULT 'NORMAL'")
            db.execSQL("ALTER TABLE books ADD COLUMN year INTEGER")
            db.execSQL("ALTER TABLE books ADD COLUMN processingStatus TEXT NOT NULL DEFAULT 'PENDING'")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_books_fileHash ON books(fileHash)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_books_seriesId ON books(seriesId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_books_seriesId_volume_number_publicationType ON books(seriesId, volume, number, publicationType)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS series (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, canonicalName TEXT COLLATE NOCASE NOT NULL, displayName TEXT NOT NULL, year INTEGER, publisher TEXT, createdAt INTEGER NOT NULL)""")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_series_canonicalName ON series(canonicalName)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS collection_relations (parentCollectionId INTEGER NOT NULL, childType TEXT NOT NULL, childId INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(parentCollectionId, childType, childId), FOREIGN KEY(parentCollectionId) REFERENCES collections(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_relations_parentCollectionId ON collection_relations(parentCollectionId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_relations_childType_childId ON collection_relations(childType, childId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS manual_overrides (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, entityType TEXT NOT NULL, entityId INTEGER NOT NULL, relationType TEXT NOT NULL, targetId INTEGER NOT NULL, action TEXT NOT NULL, createdAt INTEGER NOT NULL)""")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_manual_overrides_entityType_entityId_relationType_targetId ON manual_overrides(entityType, entityId, relationType, targetId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS import_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, status TEXT NOT NULL, totalItems INTEGER NOT NULL, processedItems INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS import_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId INTEGER NOT NULL, bookId INTEGER NOT NULL, status TEXT NOT NULL, detectedSeries TEXT, confidence INTEGER NOT NULL, requiresReview INTEGER NOT NULL, FOREIGN KEY(sessionId) REFERENCES import_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_import_items_sessionId ON import_items(sessionId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_import_items_bookId ON import_items(bookId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS pending_imports (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId INTEGER NOT NULL, sourceUri TEXT NOT NULL, status TEXT NOT NULL, FOREIGN KEY(sessionId) REFERENCES import_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_imports_sessionId ON pending_imports(sessionId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pending_imports_sessionId_sourceUri ON pending_imports(sessionId, sourceUri)")
            db.execSQL("""INSERT OR IGNORE INTO collection_relations(parentCollectionId, childType, childId, createdAt) SELECT collectionId, 'BOOK', bookId, strftime('%s','now') * 1000 FROM book_collection_cross_ref""")
        }
    }
    internal val migration8To9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN publisher TEXT")
            db.execSQL("ALTER TABLE books ADD COLUMN isbn TEXT")
            db.execSQL("""CREATE TABLE IF NOT EXISTS organization_rules (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, field TEXT NOT NULL, match TEXT NOT NULL, value TEXT NOT NULL, targetCollectionId INTEGER NOT NULL, enabled INTEGER NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(targetCollectionId) REFERENCES collections(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_organization_rules_enabled ON organization_rules(enabled)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_organization_rules_targetCollectionId ON organization_rules(targetCollectionId)")
        }
    }
    internal val migration9To10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS advanced_rules (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, scope TEXT NOT NULL, scopeValue TEXT, priority INTEGER NOT NULL, enabled INTEGER NOT NULL, createdAt INTEGER NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_advanced_rules_enabled ON advanced_rules(enabled)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_advanced_rules_priority ON advanced_rules(priority)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS rule_conditions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ruleId INTEGER NOT NULL, field TEXT NOT NULL, match TEXT NOT NULL, value TEXT NOT NULL, FOREIGN KEY(ruleId) REFERENCES advanced_rules(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_conditions_ruleId ON rule_conditions(ruleId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS rule_actions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ruleId INTEGER NOT NULL, actionType TEXT NOT NULL, targetCollectionId INTEGER, collectionName TEXT, FOREIGN KEY(ruleId) REFERENCES advanced_rules(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_actions_ruleId ON rule_actions(ruleId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_actions_targetCollectionId ON rule_actions(targetCollectionId)")
            db.execSQL("""INSERT INTO advanced_rules(id,name,scope,scopeValue,priority,enabled,createdAt) SELECT id,name,'LIBRARY',NULL,0,enabled,createdAt FROM organization_rules""")
            db.execSQL("""INSERT INTO rule_conditions(ruleId,field,match,value) SELECT id,field,match,value FROM organization_rules""")
            db.execSQL("""INSERT INTO rule_actions(ruleId,actionType,targetCollectionId,collectionName) SELECT id,'ADD_TO_COLLECTION',targetCollectionId,NULL FROM organization_rules""")
        }
    }
    @Provides @Singleton fun db(@ApplicationContext c: Context) =
        Room.databaseBuilder(c, AppDatabase::class.java, "ereader.db")
            .addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8, migration8To9, migration9To10)
            .build()
    @Provides fun books(db: AppDatabase) = db.books()
    @Provides fun progress(db: AppDatabase) = db.progress()
    @Provides fun bookmarks(db: AppDatabase) = db.bookmarks()
    @Provides fun settings(db: AppDatabase) = db.readerSettings()
    @Provides fun collections(db: AppDatabase) = db.collections()
    @Provides fun series(db: AppDatabase) = db.series()
    @Provides fun organization(db: AppDatabase) = db.organization()
    @Provides fun resolver(@ApplicationContext c: Context): ContentResolver = c.contentResolver
}
