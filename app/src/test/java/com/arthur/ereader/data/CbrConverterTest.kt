package com.arthur.ereader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream

class CbrConverterTest {
    @Test fun `recognizes supported image signatures`() {
        assertEquals("jpg", detectComicImageExtension(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())))
        assertEquals("png", detectComicImageExtension(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)))
        assertEquals("webp", detectComicImageExtension("RIFF1234WEBP".toByteArray()))
        assertNull(detectComicImageExtension("not-an-image".toByteArray()))
    }

    @Test fun `normalizes safe converted comic names`() {
        assertEquals("Minha-HQ-01", safeCbzBaseName("  Minha HQ 01.cbr"))
        assertEquals("quadrinho", safeCbzBaseName("...cbr"))
        assertEquals(80, safeCbzBaseName("a".repeat(120) + ".cbr").length)
    }

    @Test fun `stops extracted content at the configured limit`() {
        val output = ByteArrayOutputStream()
        val limited = LimitedOutputStream(output, 4)
        limited.write(byteArrayOf(1, 2, 3, 4))
        assertThrows(CbrConversionException::class.java) { limited.write(5) }
        assertEquals(4, output.size())
    }
}
