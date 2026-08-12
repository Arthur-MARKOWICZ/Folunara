package com.arthur.ereader.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class EpubExporterTest {
    @Test fun `epub contains reflow and preserved pages`() {
        val safeDiagnostics = PdfPageDiagnostics(500, 700, false, false, false, false, emptyList())
        val complexDiagnostics = safeDiagnostics.copy(hasImages = true, reasons = listOf("imagem"))
        val document = PdfReflowDocument(
            bookId = 1,
            title = "Manual & notas",
            fingerprint = "abc123",
            extractorVersion = 1,
            pages = listOf(
                PdfExtractedPage(0, 500, 700, listOf(PdfTextBlock("Texto <seguro>", emptyList())), PdfPageClassification.REFLOWABLE, safeDiagnostics),
                PdfExtractedPage(1, 500, 700, emptyList(), PdfPageClassification.COMPLEX, complexDiagnostics),
            ),
        )
        val file = File.createTempFile("exporter-test", ".epub")
        try {
            file.outputStream().use { output -> EpubExporter().export(document, output) { ONE_PIXEL_JPEG } }
            EpubExporter().validate(file)
            ZipFile(file).use { zip ->
                assertEquals("mimetype", zip.entries().nextElement().name)
                assertNotNull(zip.getEntry("OEBPS/page-1.xhtml"))
                assertNotNull(zip.getEntry("OEBPS/images/page-2.jpg"))
                val packageDocument = zip.getInputStream(zip.getEntry("OEBPS/package.opf")).bufferedReader().readText()
                assertTrue(packageDocument.contains("media-type=\"image/jpeg\""))
                val xhtml = zip.getInputStream(zip.getEntry("OEBPS/page-1.xhtml")).bufferedReader().readText()
                assertTrue(xhtml.contains("Texto &lt;seguro&gt;"))
            }
        } finally {
            file.delete()
        }
    }

    private companion object {
        val ONE_PIXEL_JPEG = java.util.Base64.getDecoder().decode(
            "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABBQJ//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAwEBPwF//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAgEBPwF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQAGPwJ//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPyF//9oADAMBAAIAAwAAABD/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAEDAQE/EH//xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAECAQE/EH//xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAE/EH//2Q==",
        )
    }
}
