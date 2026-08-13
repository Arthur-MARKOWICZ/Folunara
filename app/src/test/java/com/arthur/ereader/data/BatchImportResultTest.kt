package com.arthur.ereader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchImportResultTest {
    @Test fun `reports a successful batch`() {
        val result = BatchImportResult(total = 5, imported = 5, failures = emptyList())
        assertEquals("5 arquivos importados e adicionados à biblioteca.", result.message())
    }

    @Test fun `reports successes and failures in a partial batch`() {
        val result = BatchImportResult(total = 5, imported = 3, failures = listOf("Arquivo inválido.", "Sem acesso."))
        assertTrue(result.message().startsWith("3 de 5 arquivos foram importados; 2 falharam."))
        assertTrue(result.message().contains("Arquivo inválido."))
    }

    @Test fun `keeps the detailed error for one failed file`() {
        val result = BatchImportResult(total = 1, imported = 0, failures = listOf("PDF inválido."))
        assertEquals("PDF inválido.", result.message())
    }
}
