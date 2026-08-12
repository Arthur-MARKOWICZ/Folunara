package com.arthur.ereader.reader.pdf

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arthur.ereader.domain.model.Book
import com.arthur.ereader.domain.model.BookFormat
import com.arthur.ereader.domain.model.ContentType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfContentExtractorInstrumentedTest {
    @Test
    fun cropBoxFixtureIsClassifiedAsDamagedAndDisablesUnsafeCropping() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixture = File(context.cacheDir, "fixture-cropbox-text.pdf").apply {
            writeBytes(Base64.decode(FIXTURE, Base64.DEFAULT))
        }
        try {
            val book = Book(
                id = 99,
                title = "CropBox fixture",
                uri = Uri.fromFile(fixture).toString(),
                format = BookFormat.PDF,
                contentType = ContentType.DOCUMENT,
            )
            val document = AndroidxPdfContentExtractor(context).extract(book, "fixture")
            val page = document.pages.single()

            assertEquals(PdfPageClassification.DAMAGED, page.classification)
            assertEquals(false, page.diagnostics.contentFitSafe)
        } finally {
            fixture.delete()
        }
    }

    private companion object {
        const val FIXTURE = "JVBERi0xLjMKJeLjz9MKMSAwIG9iago8PAovUHJvZHVjZXIgKHB5cGRmKQo+PgplbmRvYmoKMiAwIG9iago8PAovVHlwZSAvUGFnZXMKL0NvdW50IDEKL0tpZHMgWyA0IDAgUiBdCj4+CmVuZG9iagozIDAgb2JqCjw8Ci9UeXBlIC9DYXRhbG9nCi9QYWdlcyAyIDAgUgo+PgplbmRvYmoKNCAwIG9iago8PAovQ29udGVudHMgNSAwIFIKL01lZGlhQm94IFsgMCAwIDU5NS4yNzU2IDg0MS44ODk4IF0KL1Jlc291cmNlcyA8PAovRm9udCA2IDAgUgovUHJvY1NldCBbIC9QREYgL1RleHQgL0ltYWdlQiAvSW1hZ2VDICAvSW1hZ2VJIF0KPj4KL1JvdGF0ZSAwCi9UcmFucyA8PAo+PgovVHlwZSAvUGFnZQovQ3JvcEJveCBbIDAuMCAwLjAgMzk2Ljg1MDM5NCA4NDEuODg5NzY0IF0KL1BhcmVudCAyIDAgUgo+PgplbmRvYmoKNSAwIG9iago8PAovRmlsdGVyIFsgL0FTQ0lJODVEZWNvZGUgL0ZsYXRlRGVjb2RlIF0KL0xlbmd0aCAzNTUKPj4Kc3RyZWFtCkdhcW8/OFBkNVgmO0JUTyglMkRKTiRscCFvTDtLczxkKGdMKE5vdDQ8PmxbT09uLypgIXUpOzgsQylhc1hTOUNPbjUuV0tkajxBLWZIMVxSSnQuSSYoLjQqOlBhdGo5NWkmaWgmUEtdcVFyZipOIWk3PkFdL2c9X04tIWQ3XSxeYGRsaHMzR1JJTUY2cGFQai8tYUQmcVI1KDI+WDdebyh1Kl9uKGgjISFjV1w+bW5kOigiP1BUSEhvVlNdKVBbQic5WVlyYl5dW1twY0ZxWUgkbSdKQ1hSWUAiWjJES007a1JTL1lVU0hrNWUkSlQ/YFQ+WUNjKW5YaklzPz9lcloxTVo5I0IiNU8pZFEsRUwrWGJoSV9vO1I8KTsxL0YjIUwhUWprQD8vWHJzYCFGOCg1RVI5RGlNZigkMW0qXl9KJXQ5ZFllblVERDhQZl1WT1ZGN1hTXnQ5YzkpI2xJcmJ+PgplbmRzdHJlYW0KZW5kb2JqCjYgMCBvYmoKPDwKL0YxIDcgMCBSCi9GMiA4IDAgUgo+PgplbmRvYmoKNyAwIG9iago8PAovQmFzZUZvbnQgL0hlbHZldGljYQovRW5jb2RpbmcgL1dpbkFuc2lFbmNvZGluZwovTmFtZSAvRjEKL1N1YnR5cGUgL1R5cGUxCi9UeXBlIC9Gb250Cj4+CmVuZG9iago4IDAgb2JqCjw8Ci9CYXNlRm9udCAvSGVsdmV0aWNhLUJvbGQKL0VuY29kaW5nIC9XaW5BbnNpRW5jb2RpbmcKL05hbWUgL0YyCi9TdWJ0eXBlIC9UeXBlMQovVHlwZSAvRm9udAo+PgplbmRvYmoKeHJlZgowIDkKMDAwMDAwMDAwMCA2NTUzNSBmIAowMDAwMDAwMDE1IDAwMDAwIG4gCjAwMDAwMDAwNTQgMDAwMDAgbiAKMDAwMDAwMDExMyAwMDAwMCBuIAowMDAwMDAwMTYyIDAwMDAwIG4gCjAwMDAwMDA0MDQgMDAwMDAgbiAKMDAwMDAwMDg1MCAwMDAwMCBuIAowMDAwMDAwODkxIDAwMDAwIG4gCjAwMDAwMDA5OTggMDAwMDAgbiAKdHJhaWxlcgo8PAovU2l6ZSA5Ci9Sb290IDMgMCBSCi9JbmZvIDEgMCBSCj4+CnN0YXJ0eHJlZgoxMTEwCiUlRU9GCg=="
    }
}
