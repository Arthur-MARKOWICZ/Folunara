package com.arthur.ereader.domain.organization

import com.arthur.ereader.data.metadata.PublicationMetadata
import com.arthur.ereader.domain.model.OrganizationChildType
import com.arthur.ereader.domain.model.PublicationType
import com.arthur.ereader.domain.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganizationEngineTest {
    @Test fun `metadata wins over filename and produces high confidence suggestion`() {
        val result = OrganizationEngine.analyze(
            7,
            "nome-incorreto-99.cbz",
            PublicationMetadata(series = "Absolute Batman", number = 5.0, volume = 1.0, format = "Special"),
        )

        assertEquals("Absolute Batman", result.detectedSeries)
        assertEquals(5.0, result.number)
        assertEquals(PublicationType.SPECIAL, result.publicationType)
        assertEquals(99, result.confidence)
        assertFalse(result.requiresConfirmation)
    }

    @Test fun `filename infers manga volume series and content type`() {
        val result = OrganizationEngine.analyze(1, "One Piece Vol. 104.cbz", null)

        assertEquals("One Piece", result.detectedSeries)
        assertEquals(104.0, result.volume)
        assertEquals(PublicationType.VOLUME, result.publicationType)
        assertEquals(ContentType.MANGA, result.suggestedContentType)
        assertFalse(result.requiresConfirmation)
    }

    @Test fun `literary filename extracts author series and volume`() {
        val result = OrganizationEngine.analyze(2, "Frank Herbert - Duna - 02 - O Messias.epub", null)
        assertEquals("Duna", result.detectedSeries)
        assertEquals("Frank Herbert", result.suggestedAuthor)
        assertEquals(2.0, result.volume)
        assertEquals(ContentType.BOOK, result.suggestedContentType)
    }

    @Test fun `reports only integer gaps between known normal issues`() {
        assertEquals(listOf(4), OrganizationEngine.possibleMissingNumbers(listOf(1.0, 2.0, 3.0, 5.0, 5.5)))
    }

    @Test fun `hierarchy allows multiple parents and rejects a cycle`() {
        val valid = listOf(
            HierarchyEdge(1, OrganizationChildType.COLLECTION, 3),
            HierarchyEdge(2, OrganizationChildType.COLLECTION, 3),
            HierarchyEdge(3, OrganizationChildType.SERIES, 10),
        )
        HierarchyValidator.validate(valid)

        val error = runCatching { HierarchyValidator.validate(valid + HierarchyEdge(3, OrganizationChildType.COLLECTION, 1)) }
        assertTrue(error.isFailure)
        assertTrue(error.exceptionOrNull()?.message.orEmpty().contains("ciclos"))
    }

    @Test fun `hierarchy blocks level nine`() {
        val eightCollectionLevels = (1L..7L).map { HierarchyEdge(it, OrganizationChildType.COLLECTION, it + 1) }
        HierarchyValidator.validate(eightCollectionLevels)

        val ninthLevel = eightCollectionLevels + HierarchyEdge(8, OrganizationChildType.SERIES, 99)
        assertTrue(runCatching { HierarchyValidator.validate(ninthLevel) }.isFailure)
    }
}
