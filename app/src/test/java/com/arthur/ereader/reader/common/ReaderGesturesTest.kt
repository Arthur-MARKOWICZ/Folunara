package com.arthur.ereader.reader.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderGesturesTest {
    @Test fun `swipe changes page only after threshold at normal zoom`() {
        assertEquals(0, pageTurnForSwipe(-100f, 1f, 1000))
        assertEquals(1, pageTurnForSwipe(-250f, 1f, 1000))
        assertEquals(-1, pageTurnForSwipe(250f, 1f, 1000))
    }
    @Test fun `zoomed content consumes page swipe`() { assertEquals(0, pageTurnForSwipe(-500f, 1.2f, 1000)) }

    @Test fun `double tap requires platform timeout and slop`() {
        assertTrue(isDoubleTap(1_000, 1_250, 20f, 15f, 300, 100f))
        assertFalse(isDoubleTap(1_000, 1_301, 0f, 0f, 300, 100f))
        assertFalse(isDoubleTap(1_000, 1_200, 100f, 100f, 300, 100f))
    }
}
