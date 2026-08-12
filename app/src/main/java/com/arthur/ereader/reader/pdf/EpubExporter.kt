package com.arthur.ereader.reader.pdf

import java.io.File
import java.io.OutputStream
import java.io.ByteArrayInputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

class EpubExporter {
    fun export(
        document: PdfReflowDocument,
        output: OutputStream,
        preservedPageJpeg: (Int) -> ByteArray,
    ) {
        require(document.pages.isNotEmpty()) { "O documento não possui conteúdo exportável." }
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putStored("mimetype", EPUB_MIME.toByteArray())
            zip.putText("META-INF/container.xml", containerXml())
            zip.putText("OEBPS/styles.css", styles())
            zip.putText("OEBPS/nav.xhtml", navigation(document))
            document.pages.forEach { page ->
                if (page.classification == PdfPageClassification.REFLOWABLE) {
                    zip.putText("OEBPS/page-${page.index + 1}.xhtml", reflowPage(page))
                } else {
                    val bytes = preservedPageJpeg(page.index)
                    require(bytes.isNotEmpty()) { "Falha ao preservar a página ${page.index + 1}." }
                    zip.putBytes("OEBPS/images/page-${page.index + 1}.jpg", bytes)
                    zip.putText("OEBPS/page-${page.index + 1}.xhtml", imagePage(page))
                }
            }
            zip.putText("OEBPS/package.opf", packageDocument(document))
        }
    }

    fun validate(file: File) {
        ZipFile(file).use { zip ->
            require(zip.getEntry("mimetype") != null)
            require(zip.getInputStream(zip.getEntry("mimetype")).bufferedReader().readText() == EPUB_MIME)
            require(zip.getEntry("META-INF/container.xml") != null)
            require(zip.getEntry("OEBPS/package.opf") != null)
            require(zip.getEntry("OEBPS/nav.xhtml") != null)
            val parser = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder()
            zip.entries().asSequence()
                .filter { it.name.endsWith(".xml") || it.name.endsWith(".opf") || it.name.endsWith(".xhtml") }
                .forEach { entry ->
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    require(!bytes.toString(Charsets.UTF_8).contains("<!DOCTYPE", ignoreCase = true)) {
                        "DOCTYPE não é permitido em ${entry.name}."
                    }
                    ByteArrayInputStream(bytes).use(parser::parse)
                }
        }
    }

    private fun containerXml() = """<?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="OEBPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles>
        </container>""".trimIndent()

    private fun styles() = """
        :root { color-scheme: light dark; }
        body { font-family: serif; line-height: 1.45; margin: 6%; }
        p { margin: 0 0 0.9em; orphans: 2; widows: 2; }
        .preserved { margin: 0; padding: 0; text-align: center; }
        .preserved img { display: block; max-width: 100%; height: auto; margin: auto; }
    """.trimIndent()

    private fun navigation(document: PdfReflowDocument) = """<?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
        <head><title>${document.title.xml()}</title></head><body><nav epub:type="toc"><h1>Sumário</h1><ol>
        ${document.pages.joinToString("\n") { "<li><a href=\"page-${it.index + 1}.xhtml\">Página ${it.index + 1}</a></li>" }}
        </ol></nav></body></html>""".trimIndent()

    private fun reflowPage(page: PdfExtractedPage): String {
        val paragraphs = page.reflowText.split(Regex("\\n\\s*\\n"))
            .filter { it.isNotBlank() }
            .joinToString("\n") { "<p>${it.xml().replace("\n", "<br/>")}</p>" }
        return xhtml("Página ${page.index + 1}", paragraphs)
    }

    private fun imagePage(page: PdfExtractedPage) = xhtml(
        "Página ${page.index + 1}",
        "<div class=\"preserved\"><img src=\"images/page-${page.index + 1}.jpg\" alt=\"Página ${page.index + 1} preservada como imagem\"/></div>",
    )

    private fun xhtml(title: String, body: String) = """<?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml"><head><title>${title.xml()}</title>
        <link rel="stylesheet" type="text/css" href="styles.css"/></head><body>$body</body></html>""".trimIndent()

    private fun packageDocument(document: PdfReflowDocument): String {
        val manifestPages = document.pages.joinToString("\n") { page ->
            "<item id=\"page-${page.index + 1}\" href=\"page-${page.index + 1}.xhtml\" media-type=\"application/xhtml+xml\"/>" +
                if (page.classification != PdfPageClassification.REFLOWABLE) {
                    "\n<item id=\"image-${page.index + 1}\" href=\"images/page-${page.index + 1}.jpg\" media-type=\"image/jpeg\"/>"
                } else ""
        }
        val spine = document.pages.joinToString("\n") { "<itemref idref=\"page-${it.index + 1}\"/>" }
        return """<?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="book-id">urn:sha256:${document.fingerprint}</dc:identifier>
                <dc:title>${document.title.xml()}</dc:title><dc:language>pt-BR</dc:language>
                <meta property="dcterms:modified">1970-01-01T00:00:00Z</meta>
              </metadata>
              <manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="css" href="styles.css" media-type="text/css"/>$manifestPages
              </manifest><spine>$spine</spine>
            </package>""".trimIndent()
    }

    private fun ZipOutputStream.putStored(name: String, bytes: ByteArray) {
        val crc = CRC32().apply { update(bytes) }
        putNextEntry(ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        })
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.putText(name: String, value: String) = putBytes(name, value.toByteArray())
    private fun ZipOutputStream.putBytes(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun String.xml() = buildString(length) {
        this@xml.forEach { char ->
            append(when (char) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&apos;"
                else -> char
            })
        }
    }

    private companion object { const val EPUB_MIME = "application/epub+zip" }
}
