package com.arthur.ereader.data.metadata

import com.arthur.ereader.domain.model.PublicationType
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class PublicationMetadata(
    val title: String? = null,
    val series: String? = null,
    val number: Double? = null,
    val volume: Double? = null,
    val year: Int? = null,
    val publisher: String? = null,
    val storyArc: String? = null,
    val seriesGroup: String? = null,
    val format: String? = null,
    val authors: List<String> = emptyList(),
    val isbn: String? = null,
    val subjects: List<String> = emptyList(),
)

/** Reads only ComicInfo.xml and caps XML size so untrusted archives cannot exhaust memory. */
object ComicInfoParser {
    private const val MAX_XML_BYTES = 512 * 1024

    fun parse(archive: InputStream): PublicationMetadata? = ZipInputStream(archive.buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: return null
            if (!entry.isDirectory && entry.name.substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true)) {
                val bytes = zip.readNBytes(MAX_XML_BYTES + 1)
                require(bytes.size <= MAX_XML_BYTES) { "ComicInfo.xml excede o limite seguro." }
                return parseXml(bytes.inputStream())
            }
            zip.closeEntry()
        }
        null
    }

    internal fun parseXml(input: InputStream): PublicationMetadata {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isExpandEntityReferences = false
        }
        val root = factory.newDocumentBuilder().parse(input).documentElement
        fun value(name: String) = root.getElementsByTagName(name).item(0)?.textContent?.trim()?.takeIf(String::isNotBlank)
        return PublicationMetadata(
            title = value("Title"),
            series = value("Series"),
            number = value("Number")?.normalizedNumber(),
            volume = value("Volume")?.normalizedNumber(),
            year = value("Year")?.toIntOrNull(),
            publisher = value("Publisher"),
            storyArc = value("StoryArc"),
            seriesGroup = value("SeriesGroup"),
            format = value("Format"),
        )
    }
}

private fun String.normalizedNumber() = replace(',', '.').toDoubleOrNull()

fun PublicationMetadata.publicationType(fileName: String): PublicationType {
    val source = listOfNotNull(format, title, fileName).joinToString(" ").lowercase()
    return when {
        Regex("\\bannual\\b").containsMatchIn(source) -> PublicationType.ANNUAL
        Regex("\\bspecial\\b").containsMatchIn(source) -> PublicationType.SPECIAL
        Regex("\\b(one[ -]?shot|oneshot)\\b").containsMatchIn(source) -> PublicationType.ONE_SHOT
        Regex("\\b(vol(?:ume)?[ ._-]*\\d+)\\b").containsMatchIn(source) -> PublicationType.VOLUME
        else -> PublicationType.NORMAL
    }
}
