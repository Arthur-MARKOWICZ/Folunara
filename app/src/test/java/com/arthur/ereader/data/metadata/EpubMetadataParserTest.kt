package com.arthur.ereader.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubMetadataParserTest {
    @Test fun `extracts literary series author publisher and isbn`() {
        val container = """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>"""
        val opf = """<package xmlns="http://www.idpf.org/2007/opf" xmlns:opf="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/"><metadata><dc:title>O Messias de Duna</dc:title><dc:creator>Frank Herbert</dc:creator><dc:publisher>Aleph</dc:publisher><dc:identifier opf:scheme="ISBN">9788576573135</dc:identifier><dc:subject>Ficção científica</dc:subject><meta name="calibre:series" content="Duna"/><meta name="calibre:series_index" content="2"/></metadata></package>"""
        val epub = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                listOf("META-INF/container.xml" to container, "OPS/book.opf" to opf).forEach { (name, value) ->
                    zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry()
                }
            }
        }.toByteArray()

        val metadata = requireNotNull(EpubMetadataParser.parse(epub.inputStream()))
        assertEquals("O Messias de Duna", metadata.title)
        assertEquals(listOf("Frank Herbert"), metadata.authors)
        assertEquals("Duna", metadata.series)
        assertEquals(2.0, metadata.volume)
        assertEquals("Aleph", metadata.publisher)
        assertEquals("9788576573135", metadata.isbn)
    }

    @Test fun `reads epub3 collection metadata`() {
        val opf = """<package xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/"><metadata><dc:title>Leviatã Desperta</dc:title><meta property="belongs-to-collection">The Expanse</meta><meta property="group-position">1</meta></metadata></package>"""
        val metadata = EpubMetadataParser.parsePackage(opf.byteInputStream())
        assertEquals("The Expanse", metadata.series)
        assertEquals(1.0, metadata.number)
    }
}
