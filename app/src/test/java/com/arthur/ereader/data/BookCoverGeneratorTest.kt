package com.arthur.ereader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BookCoverGeneratorTest {
    @Test fun `large portrait cover fits target without distortion`() {
        assertEquals(600 to 900, fittedCoverSize(2_000, 3_000))
    }

    @Test fun `small cover is not enlarged`() {
        assertEquals(300 to 450, fittedCoverSize(300, 450))
    }

    @Test fun `wide first page respects width limit`() {
        assertEquals(600 to 300, fittedCoverSize(2_000, 1_000))
    }
}
