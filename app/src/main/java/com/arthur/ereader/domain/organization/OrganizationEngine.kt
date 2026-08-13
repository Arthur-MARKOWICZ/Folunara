package com.arthur.ereader.domain.organization

import com.arthur.ereader.data.metadata.PublicationMetadata
import com.arthur.ereader.data.metadata.publicationType
import com.arthur.ereader.domain.model.ContentType
import com.arthur.ereader.domain.model.OrganizationSuggestion
import com.arthur.ereader.domain.model.PublicationType

object OrganizationEngine {
    private val explicitIssue = Regex("""(?i)^(.*?)(?:\s*[-._ ]+)?(?:#|no\.?\s*)(\d+(?:[.,]\d+)?)\b""")
    private val volume = Regex("""(?i)^(.*?)(?:\s+|\s*[-–—:_]\s*)(?:v|vol(?:ume)?|tomo|book|livro)\.?\s*0*(\d+(?:[.,]\d+)?)\b""")
    private val chapter = Regex("""(?i)^(.*?)(?:\s+|\s*[-–—:_]\s*)(?:ch(?:apter)?|cap(?:[íi]tulo)?)\.?\s*0*(\d+(?:[.,]\d+)?)\b""")
    private val indexedTitle = Regex("""(?i)^(.+?)\s*[-–—]\s*0*(\d{1,3})(?:\s*[-–—]\s*.+)?$""")
    private val authorSeriesIndex = Regex("""(?i)^(.+?)\s*[-–—]\s*(.+?)\s*[-–—]\s*0*(\d{1,3})\s*[-–—]\s*.+$""")
    private val parentheticalIndex = Regex("""(?i)^(.+?)\s*\((?:book|livro|vol(?:ume)?|#)\s*0*(\d+(?:[.,]\d+)?)\)""")
    private val trailingNumber = Regex("""(?i)^(.*?)[\s._-]+0*(\d+(?:[.,]\d+)?)$""")
    private val removableTags = Regex("""(?i)\s*[\[(](?:digital|retail|web|scan|scans|pt-?br|english|portugu[eê]s|\d{3,4}p|c2c|fixed|ocr)[\])]\s*""")

    fun analyze(bookId: Long, fileName: String, metadata: PublicationMetadata?): OrganizationSuggestion {
        val baseName = cleanBaseName(fileName)
        val metadataInference = metadata?.title?.let(::cleanBaseName)?.let(::inferFilename)
        val inferred = metadataInference?.takeIf { it.series != null } ?: inferFilename(baseName)
        val detectedSeries = metadata?.series?.canonicalName() ?: inferred.series
        val publicationType = metadata?.publicationType(fileName)?.takeUnless {
            it == PublicationType.NORMAL && inferred.publicationType != PublicationType.NORMAL
        } ?: inferred.publicationType
        val inferredNumber = metadata?.number ?: if (publicationType == PublicationType.VOLUME) null else inferred.number
        val inferredVolume = metadata?.volume ?: inferred.volume
        val hasMetadataSeries = !metadata?.series.isNullOrBlank()
        val confidence = when {
            hasMetadataSeries && (metadata?.number != null || metadata?.volume != null) -> 99
            hasMetadataSeries -> 96
            inferred.series != null -> inferred.confidence
            else -> 20
        }
        val contentType = classifyContent(fileName, metadata, publicationType, inferred.isManga)
        val warnings = buildList {
            if (detectedSeries == null) add("Série não identificada")
            if (confidence < 80) add("Identificação com baixa confiança")
            if (contentType == ContentType.MANGA && metadata?.subjects.orEmpty().isEmpty()) add("Classificado como mangá pelo nome do arquivo")
        }
        return OrganizationSuggestion(
            bookId = bookId,
            detectedSeries = detectedSeries,
            confidence = confidence,
            publicationType = publicationType,
            volume = inferredVolume,
            number = inferredNumber,
            warnings = warnings,
            requiresConfirmation = detectedSeries == null || confidence < 90,
            suggestedContentType = contentType,
            suggestedAuthor = inferred.author,
        )
    }

    fun possibleMissingNumbers(numbers: Iterable<Double?>): List<Int> {
        val values = numbers.mapNotNull { it?.takeIf { number -> number % 1.0 == 0.0 }?.toInt() }.distinct().sorted()
        if (values.size < 2) return emptyList()
        return (values.first()..values.last()).filterNot(values.toSet()::contains)
    }

    private fun inferFilename(baseName: String): FilenameInference {
        val literary = authorSeriesIndex.find(baseName)
        if (literary != null) return FilenameInference(
            series = literary.groupValues[2].canonicalName(),
            volume = literary.groupValues[3].number(),
            publicationType = PublicationType.VOLUME,
            confidence = 84,
            author = literary.groupValues[1].canonicalName(),
        )
        val volumeMatch = volume.find(baseName)
        if (volumeMatch != null) return FilenameInference(
            series = volumeMatch.groupValues[1].canonicalName(),
            volume = volumeMatch.groupValues[2].number(),
            publicationType = PublicationType.VOLUME,
            confidence = 91,
            isManga = Regex("""(?i)\b(v|vol|tomo)\b""").containsMatchIn(volumeMatch.value),
        )
        val chapterMatch = chapter.find(baseName)
        if (chapterMatch != null) return FilenameInference(
            series = chapterMatch.groupValues[1].canonicalName(),
            number = chapterMatch.groupValues[2].number(),
            confidence = 88,
            isManga = true,
        )
        val issueMatch = explicitIssue.find(baseName)
        if (issueMatch != null) return FilenameInference(issueMatch.groupValues[1].canonicalName(), number = issueMatch.groupValues[2].number(), confidence = 88)
        val parenthetical = parentheticalIndex.find(baseName)
        if (parenthetical != null) return FilenameInference(parenthetical.groupValues[1].canonicalName(), volume = parenthetical.groupValues[2].number(), publicationType = PublicationType.VOLUME, confidence = 87)
        val indexed = indexedTitle.find(baseName)
        if (indexed != null && indexed.groupValues[2].toIntOrNull() !in 1900..2100) {
            return FilenameInference(indexed.groupValues[1].canonicalName(), volume = indexed.groupValues[2].number(), publicationType = PublicationType.VOLUME, confidence = 80)
        }
        val trailing = trailingNumber.find(baseName)
        if (trailing != null && trailing.groupValues[2].toIntOrNull() !in 1900..2100) {
            return FilenameInference(trailing.groupValues[1].canonicalName(), number = trailing.groupValues[2].number(), confidence = 74)
        }
        return FilenameInference(series = null)
    }

    private fun classifyContent(
        fileName: String,
        metadata: PublicationMetadata?,
        publicationType: PublicationType,
        filenameManga: Boolean,
    ): ContentType {
        val source = (metadata?.subjects.orEmpty() + listOf(fileName, metadata?.format.orEmpty())).joinToString(" ").lowercase()
        val manga = filenameManga || listOf("manga", "mangá", "manhwa", "manhua", "light novel manga").any(source::contains)
        val comic = listOf("comic", "comics", "quadrinho", "graphic novel", "hq").any(source::contains)
        return when {
            manga -> ContentType.MANGA
            comic || fileName.endsWith(".cbz", true) || fileName.endsWith(".cbr", true) -> ContentType.COMIC
            fileName.endsWith(".epub", true) -> ContentType.BOOK
            publicationType == PublicationType.VOLUME && !metadata?.authors.isNullOrEmpty() -> ContentType.BOOK
            else -> ContentType.DOCUMENT
        }
    }

    private fun cleanBaseName(fileName: String): String = fileName.substringBeforeLast('.')
        .replace('_', ' ')
        .replace(removableTags, " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun String.canonicalName(): String = trim(' ', '-', '_', '.', '#', ':')
        .replace(Regex("\\s+"), " ")
        .trim()
    private fun String.number() = replace(',', '.').toDoubleOrNull()

    private data class FilenameInference(
        val series: String?,
        val number: Double? = null,
        val volume: Double? = null,
        val publicationType: PublicationType = PublicationType.NORMAL,
        val confidence: Int = 20,
        val isManga: Boolean = false,
        val author: String? = null,
    )
}
