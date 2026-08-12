package com.arthur.ereader.reader.comic

import org.junit.Assert.assertEquals
import org.junit.Test

class ComicMemoryTest {
    @Test fun `large comic pages are sampled before decoding`() {
        assertEquals(1, comicSampleSize(2000, 3000))
        assertEquals(8, comicSampleSize(12000, 18000))
    }

    @Test fun `invalid dimensions use safe default`() {
        assertEquals(1, comicSampleSize(0, 100))
    }
}
