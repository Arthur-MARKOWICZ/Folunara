package com.arthur.ereader.data.metadata

import com.arthur.ereader.domain.model.PublicationType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ComicInfoParserTest {
    @Test fun `reads supported ComicInfo fields from cbz`() {
        val xml = """<ComicInfo><Title>O começo</Title><Series>Absolute Batman</Series><Number>5</Number><Volume>2</Volume><Year>2025</Year><Publisher>DC</Publisher><Format>Annual</Format></ComicInfo>"""
        val archive = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("metadata/ComicInfo.xml"))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val result = requireNotNull(ComicInfoParser.parse(archive.inputStream()))

        assertEquals("Absolute Batman", result.series)
        assertEquals(5.0, result.number)
        assertEquals(2.0, result.volume)
        assertEquals(2025, result.year)
        assertEquals(PublicationType.ANNUAL, result.publicationType("arquivo.cbz"))
    }
}
