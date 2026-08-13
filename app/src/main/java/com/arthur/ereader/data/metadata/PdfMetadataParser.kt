package com.arthur.ereader.data.metadata

import java.io.InputStream

/** Best-effort parser for standard PDF Info fields; filename heuristics remain the fallback. */
object PdfMetadataParser {
    private const val MAX_SCAN_BYTES = 4 * 1024 * 1024

    fun parse(input: InputStream): PublicationMetadata? {
        val source = input.readNBytes(MAX_SCAN_BYTES).toString(Charsets.ISO_8859_1)
        fun literal(name: String): String? {
            val value = Regex("""/$name\s*\(((?:\\.|[^\\)])*)\)""", RegexOption.IGNORE_CASE)
                .find(source)?.groupValues?.get(1) ?: return null
            val unescaped = value.replace("\\(", "(").replace("\\)", ")").replace("\\n", " ").replace("\\r", " ").replace("\\\\", "\\").trim()
            val utf8 = unescaped.toByteArray(Charsets.ISO_8859_1).toString(Charsets.UTF_8)
            return utf8.takeIf { '\uFFFD' !in it }?.trim()?.takeIf(String::isNotBlank)
                ?: unescaped.takeIf(String::isNotBlank)
        }
        fun keyword(label: String): String? = literal("Keywords")?.let {
            Regex("(?i)(?:^|[;,])\\s*(?:$label)\\s*[:=]\\s*([^;,]+)").find(it)?.groupValues?.get(1)?.trim()
        }
        val series = literal("Series") ?: keyword("series|série")
        val volume = (literal("Volume") ?: keyword("volume|vol"))?.replace(',', '.')?.toDoubleOrNull()
        val isbn = (literal("ISBN") ?: keyword("isbn"))?.normalizeIsbn()?.takeIf { it.length in setOf(10, 13) }
        val result = PublicationMetadata(
            title = literal("Title"),
            series = series,
            number = (literal("SeriesIndex") ?: keyword("seriesindex|number|número"))?.replace(',', '.')?.toDoubleOrNull(),
            volume = volume,
            publisher = literal("Publisher") ?: keyword("publisher|editora"),
            authors = listOfNotNull(literal("Author")),
            isbn = isbn,
            subjects = listOfNotNull(literal("Subject")),
            format = "PDF",
        )
        return result.takeIf {
            it.title != null || it.series != null || it.authors.isNotEmpty() || it.publisher != null || it.isbn != null
        }
    }
}
