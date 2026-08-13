package com.arthur.ereader.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfMetadataParserTest {
    @Test fun `extracts standard info and local organization keywords`() {
        val pdfFragment = "%PDF-1.7\n<< /Title (Fundação e Império) /Author (Isaac Asimov) /Keywords (Series: Fundação; Volume: 2; ISBN: 9780000000002) /Publisher (Aleph) >>"
        val metadata = requireNotNull(PdfMetadataParser.parse(pdfFragment.byteInputStream()))
        assertEquals("Fundação e Império", metadata.title)
        assertEquals(listOf("Isaac Asimov"), metadata.authors)
        assertEquals("Fundação", metadata.series)
        assertEquals(2.0, metadata.volume)
        assertEquals("Aleph", metadata.publisher)
        assertEquals("9780000000002", metadata.isbn)
    }
}
