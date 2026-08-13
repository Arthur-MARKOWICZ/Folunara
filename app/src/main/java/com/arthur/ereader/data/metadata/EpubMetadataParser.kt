package com.arthur.ereader.data.metadata

import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Extracts package metadata without rendering or loading the whole EPUB in memory. */
object EpubMetadataParser {
    private const val MAX_ENTRIES = 10_000
    private const val MAX_XML_BYTES = 1024 * 1024

    fun parse(input: InputStream): PublicationMetadata? {
        var containerXml: ByteArray? = null
        val packages = linkedMapOf<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            repeat(MAX_ENTRIES) {
                val entry = zip.nextEntry ?: return@use
                if (!entry.isDirectory) {
                    val normalized = entry.name.replace('\\', '/').trimStart('/')
                    if (normalized.equals("META-INF/container.xml", true) || normalized.endsWith(".opf", true)) {
                        val bytes = zip.readNBytes(MAX_XML_BYTES + 1)
                        require(bytes.size <= MAX_XML_BYTES) { "Metadados EPUB excedem o limite seguro." }
                        if (normalized.equals("META-INF/container.xml", true)) containerXml = bytes
                        else if (packages.size < 16) packages[normalized] = bytes
                    }
                }
                zip.closeEntry()
            }
        }
        if (packages.isEmpty()) return null
        val rootPath = containerXml?.let(::packagePath)
        val opf = packages.entries.firstOrNull { it.key.equals(rootPath, true) }?.value ?: packages.values.first()
        return parsePackage(opf.inputStream())
    }

    internal fun parsePackage(input: InputStream): PublicationMetadata {
        val document = secureFactory(namespaceAware = true).newDocumentBuilder().parse(input)
        val all = document.getElementsByTagName("*")
        fun elements(localName: String) = (0 until all.length).mapNotNull { all.item(it) as? Element }
            .filter { it.localName.equals(localName, true) || it.tagName.substringAfter(':').equals(localName, true) }
        fun texts(localName: String) = elements(localName).mapNotNull { it.textContent?.trim()?.takeIf(String::isNotBlank) }
        val metas = elements("meta")
        fun metaByName(name: String) = metas.firstOrNull { it.getAttribute("name").equals(name, true) }
            ?.getAttribute("content")?.trim()?.takeIf(String::isNotBlank)
        fun metaByProperty(property: String) = metas.firstOrNull { it.getAttribute("property").equals(property, true) }
            ?.textContent?.trim()?.takeIf(String::isNotBlank)

        val identifiers = elements("identifier")
        val isbn = identifiers.firstNotNullOfOrNull { element ->
            val scheme = element.getAttribute("opf:scheme").ifBlank { element.getAttribute("scheme") }
            val value = element.textContent?.trim().orEmpty()
            value.normalizeIsbn().takeIf { scheme.equals("ISBN", true) || value.contains("isbn", true) || it.length in setOf(10, 13) }
        }
        val series = metaByName("calibre:series") ?: metaByProperty("belongs-to-collection")
        val seriesIndex = metaByName("calibre:series_index") ?: metaByProperty("group-position")
        val date = texts("date").firstOrNull()
        return PublicationMetadata(
            title = texts("title").firstOrNull(),
            series = series,
            number = seriesIndex?.replace(',', '.')?.toDoubleOrNull(),
            volume = seriesIndex?.replace(',', '.')?.toDoubleOrNull(),
            year = date?.take(4)?.toIntOrNull(),
            publisher = texts("publisher").firstOrNull(),
            format = "EPUB",
            authors = texts("creator").distinct(),
            isbn = isbn,
            subjects = texts("subject").distinct(),
        )
    }

    private fun packagePath(bytes: ByteArray): String? {
        val document = secureFactory(namespaceAware = true).newDocumentBuilder().parse(bytes.inputStream())
        val roots = document.getElementsByTagNameNS("*", "rootfile")
        return (roots.item(0) as? Element)?.getAttribute("full-path")?.replace('\\', '/')?.trimStart('/')
    }

    private fun secureFactory(namespaceAware: Boolean) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = namespaceAware
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isExpandEntityReferences = false
    }
}

internal fun String.normalizeIsbn(): String = removePrefix("urn:isbn:")
    .replace(Regex("(?i)isbn(?:-1[03])?[: ]*"), "")
    .filter { it.isDigit() || it.equals('X', true) }
    .uppercase()
