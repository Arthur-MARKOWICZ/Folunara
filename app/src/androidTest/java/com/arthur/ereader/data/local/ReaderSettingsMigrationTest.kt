package com.arthur.ereader.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arthur.ereader.core.di.AppModule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSettingsMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun close() {
        helper?.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun migration2To9PreservesSettingsAndAddsOrganization() {
        context.deleteDatabase(DB_NAME)
        val callback = object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE books (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, author TEXT, uri TEXT NOT NULL, format TEXT NOT NULL, contentType TEXT NOT NULL, fileSize INTEGER NOT NULL, dateAdded INTEGER NOT NULL, lastReadAt INTEGER, favorite INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE reader_settings (id INTEGER NOT NULL, epubFontScale REAL NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE epub_book_settings (bookId INTEGER NOT NULL, fontScale REAL NOT NULL, PRIMARY KEY(bookId))")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(DB_NAME).callback(callback).build()
        )
        val db = helper!!.writableDatabase
        db.execSQL("INSERT INTO books (id,title,author,uri,format,contentType,fileSize,dateAdded,lastReadAt,favorite) VALUES (7,'Livro',NULL,'content://book','EPUB','BOOK',0,0,NULL,0)")
        db.execSQL("INSERT INTO reader_settings (id,epubFontScale) VALUES (1,1.3)")
        db.execSQL("INSERT INTO epub_book_settings (bookId,fontScale) VALUES (7,1.7)")
        db.execSQL("INSERT INTO epub_book_settings (bookId,fontScale) VALUES (999,1.9)")

        AppModule.migration2To3.migrate(db)
        db.execSQL("UPDATE book_reader_settings SET pdfFitMode='WIDTH' WHERE bookId=7")
        AppModule.migration3To4.migrate(db)
        AppModule.migration4To5.migrate(db)
        AppModule.migration5To6.migrate(db)
        AppModule.migration6To7.migrate(db)

        db.query("SELECT epubFontScale, appTheme, epubLineHeight FROM reader_settings WHERE id=1").use {
            assertTrue(it.moveToFirst())
            assertEquals(1.3f, it.getFloat(0), 0.001f)
            assertEquals("SYSTEM", it.getString(1))
            assertEquals(1.2f, it.getFloat(2), 0.001f)
        }
        db.query("SELECT epubFontScale, pdfPageMode, pdfFitMode, pdfZoomScale FROM book_reader_settings WHERE bookId=7").use {
            assertTrue(it.moveToFirst())
            assertEquals(1.7f, it.getFloat(0), 0.001f)
            assertTrue(it.isNull(1))
            assertEquals("WIDTH", it.getString(2))
            assertTrue(it.isNull(3))
        }
        db.query("SELECT COUNT(*) FROM book_reader_settings WHERE bookId=999").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        db.query("SELECT pdfReadingFontScale, pdfReadingLineHeight, pdfReadingTheme, pdfReadingLayout FROM reader_settings WHERE id=1").use {
            assertTrue(it.moveToFirst())
            assertEquals(1f, it.getFloat(0), 0.001f)
            assertEquals(1.4f, it.getFloat(1), 0.001f)
            assertEquals("LIGHT", it.getString(2))
            assertEquals("SCROLL", it.getString(3))
        }
        db.query("SELECT pdfReadingFontScale, pdfReadingTheme FROM book_reader_settings WHERE bookId=7").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
            assertTrue(it.isNull(1))
        }
        db.query("SELECT coverUri FROM books WHERE id=7").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
        }
        db.execSQL("INSERT INTO collections (id,name,description,color,createdAt,updatedAt) VALUES (3,'Estudos','Técnicos','BLUE',1,1)")
        db.execSQL("INSERT INTO book_collection_cross_ref (bookId,collectionId) VALUES (7,3)")
        AppModule.migration7To8.migrate(db)
        AppModule.migration8To9.migrate(db)
        db.query("SELECT COUNT(*) FROM book_collection_cross_ref WHERE bookId=7 AND collectionId=3").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT title FROM books WHERE id=7").use {
            assertTrue(it.moveToFirst())
            assertEquals("Livro", it.getString(0))
        }
        db.query("SELECT fileHash, seriesId, publicationType, processingStatus FROM books WHERE id=7").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
            assertTrue(it.isNull(1))
            assertEquals("NORMAL", it.getString(2))
            assertEquals("PENDING", it.getString(3))
        }
        db.query("SELECT COUNT(*) FROM collection_relations WHERE parentCollectionId=3 AND childType='BOOK' AND childId=7").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT publisher, isbn FROM books WHERE id=7").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
            assertTrue(it.isNull(1))
        }
        db.execSQL("INSERT INTO organization_rules(name,field,match,value,targetCollectionId,enabled,createdAt) VALUES ('PDFs DC','PUBLISHER','EQUALS','DC',3,1,1)")
        db.query("SELECT COUNT(*) FROM organization_rules WHERE targetCollectionId=3").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        AppModule.migration9To10.migrate(db)
        db.query("SELECT scope, priority FROM advanced_rules WHERE name='PDFs DC'").use {
            assertTrue(it.moveToFirst())
            assertEquals("LIBRARY", it.getString(0))
            assertEquals(0, it.getInt(1))
        }
        db.query("SELECT COUNT(*) FROM rule_conditions WHERE field='PUBLISHER' AND value='DC'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM rule_actions WHERE actionType='ADD_TO_COLLECTION' AND targetCollectionId=3").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    private companion object { const val DB_NAME = "settings-migration-test.db" }
}
