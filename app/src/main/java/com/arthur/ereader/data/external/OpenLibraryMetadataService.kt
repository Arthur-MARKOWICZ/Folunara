package com.arthur.ereader.data.external

import com.arthur.ereader.data.local.BookEntity
import com.arthur.ereader.domain.model.ExternalMetadataSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenLibraryMetadataService @Inject constructor() {
    suspend fun search(book: BookEntity): List<ExternalMetadataSuggestion> = withContext(Dispatchers.IO) {
        val connection = URI(createOpenLibrarySearchUrl(book.title, book.author, book.isbn)).toURL().openConnection() as HttpURLConnection
        try {
            connection.configure()
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Open Library respondeu com HTTP ${connection.responseCode}." }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val text = reader.readText()
                check(text.length <= MAX_RESPONSE_CHARS) { "Resposta externa maior que o limite permitido." }
                text
            }
            parse(body, book.isbn)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadCover(url: String): ByteArray = withContext(Dispatchers.IO) {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host.equals(COVER_HOST, ignoreCase = true)) { "Endereço de capa não permitido." }
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.configure()
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Não foi possível baixar a capa." }
            val declared = connection.contentLengthLong
            check(declared < 0 || declared <= MAX_COVER_BYTES) { "Capa maior que o limite permitido." }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    check(total <= MAX_COVER_BYTES) { "Capa maior que o limite permitido." }
                    output.write(buffer, 0, read)
                }
            }
            output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.configure() {
        connectTimeout = 8_000
        readTimeout = 12_000
        instanceFollowRedirects = false
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", USER_AGENT)
    }

    private fun parse(json: String, preferredIsbn: String?): List<ExternalMetadataSuggestion> {
        val docs = JSONObject(json).optJSONArray("docs") ?: return emptyList()
        return buildList {
            for (index in 0 until minOf(docs.length(), 10)) {
                val doc = docs.optJSONObject(index) ?: continue
                val title = doc.cleanString("title") ?: continue
                val key = doc.cleanString("key") ?: continue
                val isbns = doc.stringList("isbn")
                val seriesValue = doc.stringList("series").firstOrNull()
                val (series, number) = splitSeries(seriesValue)
                val coverId = doc.optLong("cover_i", -1).takeIf { it > 0 }
                add(
                    ExternalMetadataSuggestion(
                        providerId = key,
                        title = title,
                        authors = doc.stringList("author_name"),
                        publisher = doc.stringList("publisher").firstOrNull(),
                        year = doc.optInt("first_publish_year", -1).takeIf { it in 1..3000 },
                        isbn = chooseIsbn(isbns, preferredIsbn),
                        series = series,
                        number = number,
                        coverUrl = coverId?.let { "https://$COVER_HOST/b/id/$it-L.jpg?default=false" },
                        sourceUrl = "https://openlibrary.org$key",
                    ),
                )
            }
        }
    }

    private fun JSONObject.cleanString(key: String) = optString(key, "").trim().takeIf(String::isNotEmpty)
    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it, "").trim().takeIf(String::isNotEmpty) }
    }

    private companion object {
        const val COVER_HOST = "covers.openlibrary.org"
        const val USER_AGENT = "E-reader/0.1 (Android local library; user-initiated metadata lookup)"
        const val MAX_RESPONSE_CHARS = 2 * 1024 * 1024
        const val MAX_COVER_BYTES = 10 * 1024 * 1024
    }
}

internal fun createOpenLibrarySearchUrl(title: String, author: String?, isbn: String?): String {
    val normalizedIsbn = isbn?.filter(Char::isLetterOrDigit).orEmpty()
    val query = if (normalizedIsbn.isNotBlank()) {
        "isbn=$normalizedIsbn"
    } else {
        buildList {
            add("title=${encode(title)}")
            author?.takeIf(String::isNotBlank)?.let { add("author=${encode(it)}") }
        }.joinToString("&")
    }
    val fields = "key,title,author_name,publisher,first_publish_year,isbn,cover_i,series"
    return "https://openlibrary.org/search.json?$query&fields=$fields&limit=10"
}

private fun encode(value: String) = URLEncoder.encode(value.trim(), Charsets.UTF_8.name())

private fun chooseIsbn(values: List<String>, preferred: String?): String? {
    val wanted = preferred?.filter(Char::isLetterOrDigit)
    return values.firstOrNull { it.filter(Char::isLetterOrDigit).equals(wanted, true) }
        ?: values.firstOrNull { it.filter(Char::isLetterOrDigit).length == 13 }
        ?: values.firstOrNull()
}

private fun splitSeries(value: String?): Pair<String?, Double?> {
    if (value.isNullOrBlank()) return null to null
    val match = Regex("^(.*?)(?:\\s*[#,;:]\\s*|\\s+)(\\d+(?:\\.\\d+)?)$").matchEntire(value.trim())
    return if (match == null) value.trim() to null else match.groupValues[1].trim() to match.groupValues[2].toDoubleOrNull()
}
