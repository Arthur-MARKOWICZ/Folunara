package com.arthur.ereader.data.external

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenLibraryMetadataServiceTest {
    @Test fun isbnIsPreferredAndOnlyExpectedFieldsAreRequested() {
        val url = createOpenLibrarySearchUrl("ignored", "ignored", "978-1-23")
        assertTrue(url.startsWith("https://openlibrary.org/search.json?isbn=978123"))
        assertTrue(url.contains("fields=key,title,author_name,publisher,first_publish_year,isbn,cover_i,series"))
        assertTrue(url.endsWith("limit=10"))
        assertFalse(url.contains("author=ignored"))
    }

    @Test fun titleAndAuthorAreEncodedWhenIsbnIsUnavailable() {
        val url = createOpenLibrarySearchUrl("Cem Anos de Solidão", "García Márquez", null)
        assertTrue(url.contains("title=Cem+Anos+de+Solid%C3%A3o"))
        assertTrue(url.contains("author=Garc%C3%ADa+M%C3%A1rquez"))
    }
}
