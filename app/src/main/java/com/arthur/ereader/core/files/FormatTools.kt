package com.arthur.ereader.core.files

import com.arthur.ereader.domain.model.BookFormat

object FormatTools {
    fun fromName(name: String): BookFormat? = when (name.substringAfterLast('.', "").lowercase()) {
        "epub" -> BookFormat.EPUB; "pdf" -> BookFormat.PDF; "cbz" -> BookFormat.CBZ; else -> null
    }

    fun detect(name: String, mimeType: String?): BookFormat? = fromName(name) ?: when (mimeType?.lowercase()) {
        "application/epub+zip" -> BookFormat.EPUB
        "application/pdf" -> BookFormat.PDF
        "application/vnd.comicbook+zip", "application/x-cbz" -> BookFormat.CBZ
        else -> null
    }

    /** Natural order avoids page-10 preceding page-2 in CBZ archives. */
    fun naturalSort(names: List<String>): List<String> = names.sortedWith(compareBy<String> { it.replace(Regex("\\d+")) { it.value.padStart(12, '0') } }.thenBy { it })
}
