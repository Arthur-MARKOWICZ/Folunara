package com.arthur.ereader.data

import com.arthur.ereader.data.local.BookCollectionCrossRef
import com.arthur.ereader.data.local.BookDao
import com.arthur.ereader.data.local.BookEntity
import com.arthur.ereader.data.local.CollectionDao
import com.arthur.ereader.data.local.CollectionRelationEntity
import com.arthur.ereader.data.local.ImportItemEntity
import com.arthur.ereader.data.local.ImportSessionEntity
import com.arthur.ereader.data.local.ManualOverrideEntity
import com.arthur.ereader.data.local.OrganizationDao
import com.arthur.ereader.data.local.SeriesDao
import com.arthur.ereader.data.local.SeriesEntity
import com.arthur.ereader.data.local.PendingImportEntity
import com.arthur.ereader.data.local.AdvancedRuleEntity
import com.arthur.ereader.data.local.RuleConditionEntity
import com.arthur.ereader.data.local.RuleActionEntity
import com.arthur.ereader.data.metadata.PublicationMetadata
import com.arthur.ereader.domain.model.ManualOverrideAction
import com.arthur.ereader.domain.model.AutomationMode
import com.arthur.ereader.domain.model.OrganizationChildType
import com.arthur.ereader.domain.model.OrganizationSuggestion
import com.arthur.ereader.domain.model.ProcessingStatus
import com.arthur.ereader.domain.model.Series
import com.arthur.ereader.domain.model.BookFormat
import com.arthur.ereader.domain.model.PublicationType
import com.arthur.ereader.domain.model.BookCollection
import com.arthur.ereader.domain.model.CollectionColor
import com.arthur.ereader.domain.model.OrganizationRule
import com.arthur.ereader.domain.model.RuleField
import com.arthur.ereader.domain.model.RuleMatch
import com.arthur.ereader.domain.model.ContentType
import com.arthur.ereader.domain.model.AdvancedOrganizationRule
import com.arthur.ereader.domain.model.RuleCondition
import com.arthur.ereader.domain.model.RuleAction
import com.arthur.ereader.domain.model.RuleActionType
import com.arthur.ereader.domain.model.RuleScope
import com.arthur.ereader.domain.organization.OrganizationEngine
import com.arthur.ereader.domain.organization.AdvancedRuleEngine
import com.arthur.ereader.domain.organization.RuleEvaluationContext
import com.arthur.ereader.domain.organization.HierarchyEdge
import com.arthur.ereader.domain.organization.HierarchyValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class OrganizationReviewGroup(
    val seriesName: String?,
    val confidence: Int,
    val bookIds: List<Long>,
    val importItemIds: List<Long>,
    val suggestedCollectionId: Long? = null,
    val suggestedCollectionName: String? = null,
)

data class OrganizedBookSummary(
    val id: Long,
    val title: String,
    val format: BookFormat,
    val publicationType: PublicationType,
    val volume: Double?,
    val number: Double?,
    val author: String?,
    val publisher: String?,
    val isbn: String?,
    val contentType: ContentType,
)

data class OrganizationSearchResult(
    val type: OrganizationChildType,
    val id: Long,
    val title: String,
    val context: String? = null,
)

@Singleton
class OrganizationRepository @Inject constructor(
    private val books: BookDao,
    private val series: SeriesDao,
    private val collections: CollectionDao,
    private val organization: OrganizationDao,
) {
    fun observeSeries(): Flow<List<Series>> = series.observeAll().map { source ->
        source.map { Series(it.id, it.canonicalName, it.displayName, it.year, it.publisher, it.createdAt, it.bookCount) }
    }

    fun observeSeriesInCollection(collectionId: Long): Flow<List<Series>> = combine(
        series.observeAll(),
        organization.observeChildren(collectionId),
    ) { available, relations ->
        val ids = relations.filter { it.childType == OrganizationChildType.SERIES.name }.mapTo(mutableSetOf()) { it.childId }
        available.filter { it.id in ids }.map {
            Series(it.id, it.canonicalName, it.displayName, it.year, it.publisher, it.createdAt, it.bookCount)
        }
    }

    fun observeCollections(): Flow<List<BookCollection>> = collections.observeAll().map { source ->
        source.map { item ->
            BookCollection(
                id = item.id,
                name = item.name,
                description = item.description,
                color = runCatching { CollectionColor.valueOf(item.color) }.getOrDefault(CollectionColor.BLUE),
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
                bookCount = item.bookCount,
            )
        }
    }

    fun observeAdvancedRules(): Flow<List<AdvancedOrganizationRule>> = organization.observeAdvancedRules().map { source ->
        source.mapNotNull { it.toDomainOrNull() }
    }

    fun observeRules(): Flow<List<OrganizationRule>> = observeAdvancedRules().map { source ->
        source.mapNotNull { rule ->
            val condition = rule.conditions.singleOrNull() ?: return@mapNotNull null
            val action = rule.actions.singleOrNull()?.takeIf { it.type == RuleActionType.ADD_TO_COLLECTION } ?: return@mapNotNull null
            val target = action.targetCollectionId ?: return@mapNotNull null
            OrganizationRule(rule.id, rule.name, condition.field, condition.match, condition.value, target, rule.enabled, rule.createdAt)
        }
    }

    suspend fun createRule(name: String, field: RuleField, match: RuleMatch, value: String, targetCollectionId: Long): Result<Long> = runCatching {
        val cleanName = name.trim()
        val cleanValue = value.trim()
        require(cleanName.isNotBlank()) { "Informe um nome para a regra." }
        require(cleanValue.isNotBlank()) { "Informe o valor da condição." }
        require(collections.get(targetCollectionId) != null) { "Coleção de destino não encontrada." }
        createAdvancedRule(
            AdvancedOrganizationRule(
                name = cleanName,
                conditions = listOf(RuleCondition(field = field, match = match, value = cleanValue)),
                actions = listOf(RuleAction(type = RuleActionType.ADD_TO_COLLECTION, targetCollectionId = targetCollectionId)),
            ),
        ).getOrThrow()
    }

    suspend fun createAdvancedRule(rule: AdvancedOrganizationRule): Result<Long> = runCatching {
        AdvancedRuleEngine.validate(rule)
        rule.actions.mapNotNull(RuleAction::targetCollectionId).forEach { id ->
            require(collections.get(id) != null) { "Coleção de destino não encontrada." }
        }
        organization.insertAdvancedRule(
            AdvancedRuleEntity(name = rule.name.trim(), scope = rule.scope.name, scopeValue = rule.scopeValue?.trim()?.takeIf(String::isNotBlank), priority = rule.priority, enabled = rule.enabled, createdAt = rule.createdAt),
            rule.conditions.map { RuleConditionEntity(field = it.field.name, match = it.match.name, value = it.value.trim(), ruleId = 0) },
            rule.actions.map { RuleActionEntity(ruleId = 0, actionType = it.type.name, targetCollectionId = it.targetCollectionId, collectionName = it.collectionName?.trim()?.takeIf(String::isNotBlank)) },
        )
    }

    suspend fun setRuleEnabled(id: Long, enabled: Boolean) = organization.setAdvancedRuleEnabled(id, enabled)
    suspend fun deleteRule(id: Long) = organization.deleteAdvancedRule(id)

    fun observeCollectionParents(childType: OrganizationChildType, childId: Long): Flow<Set<Long>> =
        organization.observeParents(childType.name, childId).map(List<Long>::toSet)

    fun observeReviewGroups(): Flow<List<OrganizationReviewGroup>> = combine(organization.observeReviewItems(), collections.observeAll()) { items, availableCollections ->
        items.groupBy { it.detectedSeries?.lowercase() }.values.map { group ->
            val seriesName = group.first().detectedSeries
            val collection = seriesName?.let { name ->
                availableCollections.filter { name.contains(it.name, ignoreCase = true) }.maxByOrNull { it.name.length }
            }
            OrganizationReviewGroup(
                seriesName = seriesName,
                confidence = group.maxOf { it.confidence },
                bookIds = group.map(ImportItemEntity::bookId),
                importItemIds = group.map(ImportItemEntity::id),
                suggestedCollectionId = collection?.id,
                suggestedCollectionName = collection?.name,
            )
        }
    }

    fun search(query: String): Flow<List<OrganizationSearchResult>> = combine(
        books.observe(),
        series.observeAll(),
        collections.observeAll(),
        organization.observeRelations(),
    ) { bookItems, seriesItems, collectionItems, relations ->
        val term = query.trim()
        if (term.isBlank()) return@combine emptyList()
        val matchingCollections = collectionItems.filter { it.name.contains(term, ignoreCase = true) }.map { it.id }.toMutableSet()
        val descendantCollections = matchingCollections.toMutableSet()
        val descendantSeries = mutableSetOf<Long>()
        val descendantBooks = mutableSetOf<Long>()
        val queue = ArrayDeque(matchingCollections)
        while (queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            relations.filter { it.parentCollectionId == parent }.forEach { relation ->
                when (relation.childType) {
                    "COLLECTION" -> if (descendantCollections.add(relation.childId)) queue.add(relation.childId)
                    "SERIES" -> descendantSeries.add(relation.childId)
                    "BOOK" -> descendantBooks.add(relation.childId)
                }
            }
        }
        descendantBooks += bookItems.filter { it.seriesId in descendantSeries }.map { it.id }
        val directSeries = seriesItems.filter { it.displayName.contains(term, ignoreCase = true) }.map { it.id }.toSet()
        descendantBooks += bookItems.filter { it.seriesId in directSeries }.map { it.id }
        buildList {
            collectionItems.filter { it.id in descendantCollections || it.name.contains(term, true) }.forEach {
                add(OrganizationSearchResult(OrganizationChildType.COLLECTION, it.id, it.name, if (it.id in matchingCollections) null else "Descendente"))
            }
            seriesItems.filter { it.id in descendantSeries || it.id in directSeries }.forEach {
                add(OrganizationSearchResult(OrganizationChildType.SERIES, it.id, it.displayName, if (it.id in descendantSeries) "Em coleção encontrada" else null))
            }
            bookItems.filter {
                it.id in descendantBooks || it.title.contains(term, true) ||
                    it.author?.contains(term, true) == true || it.publisher?.contains(term, true) == true || it.isbn?.contains(term, true) == true
            }.forEach {
                add(OrganizationSearchResult(OrganizationChildType.BOOK, it.id, it.title, if (it.id in descendantBooks) "Em coleção/série encontrada" else null))
            }
        }.distinctBy { it.type to it.id }
    }

    fun observeSeriesBooks(seriesId: Long): Flow<List<OrganizedBookSummary>> = books.observeBySeries(seriesId).map { source ->
        source.map { book ->
            OrganizedBookSummary(
                id = book.id,
                title = book.title,
                format = runCatching { BookFormat.valueOf(book.format) }.getOrDefault(BookFormat.CBZ),
                publicationType = runCatching { PublicationType.valueOf(book.publicationType) }.getOrDefault(PublicationType.NORMAL),
                volume = book.volume,
                number = book.number,
                author = book.author,
                publisher = book.publisher,
                isbn = book.isbn,
                contentType = runCatching { ContentType.valueOf(book.contentType) }.getOrDefault(ContentType.DOCUMENT),
            )
        }
    }

    fun observeSeries(seriesId: Long): Flow<Series?> = series.observe(seriesId).map { item ->
        item?.let { Series(it.id, it.canonicalName, it.displayName, it.year, it.publisher, it.createdAt) }
    }

    suspend fun createSeries(name: String, year: Int?, publisher: String?): Result<Long> = runCatching {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        require(cleanName.isNotBlank()) { "Informe o nome da série." }
        require(cleanName.length <= 120) { "O nome da série deve ter até 120 caracteres." }
        require(year == null || year in 1..3000) { "Informe um ano válido." }
        require(series.find(cleanName) == null) { "Já existe uma série com esse nome." }
        series.insert(
            SeriesEntity(
                canonicalName = cleanName,
                displayName = cleanName,
                year = year,
                publisher = publisher?.trim()?.takeIf(String::isNotBlank),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun updateSeries(id: Long, displayName: String, year: Int?, publisher: String?): Result<Unit> = runCatching {
        require(series.get(id) != null) { "Série não encontrada." }
        val cleanName = displayName.trim()
        require(cleanName.isNotBlank()) { "Informe o nome de exibição." }
        series.update(id, cleanName, year, publisher?.trim()?.takeIf(String::isNotBlank))
    }

    suspend fun startImport(totalItems: Int): Long {
        val now = System.currentTimeMillis()
        return organization.createSession(ImportSessionEntity(status = "PROCESSING", totalItems = totalItems, processedItems = 0, createdAt = now, updatedAt = now))
    }

    suspend fun finishImport(sessionId: Long, processed: Int, needsReview: Boolean) {
        organization.updateSession(sessionId, if (needsReview) "NEEDS_REVIEW" else "COMPLETED", processed, System.currentTimeMillis())
    }
    suspend fun failImport(sessionId: Long, processed: Int) {
        organization.updateSession(sessionId, "FAILED", processed, System.currentTimeMillis())
    }

    suspend fun updateImportProgress(sessionId: Long, processed: Int) {
        organization.updateSession(sessionId, "PROCESSING", processed, System.currentTimeMillis())
    }

    suspend fun registerPendingImports(sessionId: Long, sourceUris: List<String>): List<PendingImportEntity> {
        organization.insertPendingImports(sourceUris.map { PendingImportEntity(sessionId = sessionId, sourceUri = it) })
        return organization.pendingImportsForSession(sessionId)
    }

    suspend fun interruptedImports() = organization.interruptedImports()

    suspend fun updatePendingImport(id: Long, status: String) = organization.updatePendingImport(id, status)

    suspend fun resumableImport() = organization.resumableSession()
    suspend fun importSession(id: Long) = organization.importSession(id)
    suspend fun hasReviewItems(sessionId: Long) = organization.hasReviewItems(sessionId)

    suspend fun processImported(
        sessionId: Long,
        book: BookEntity,
        fileName: String,
        metadata: PublicationMetadata?,
        fileHash: String,
        automationMode: AutomationMode,
    ): OrganizationSuggestion {
        books.processingStatus(book.id, ProcessingStatus.PROCESSING.name)
        val suggestion = OrganizationEngine.analyze(book.id, fileName, metadata)
        val existingSeries = suggestion.detectedSeries?.let { series.find(it) }
        val blocked = existingSeries?.let { candidate ->
            organization.overrides("BOOK", book.id).any {
                it.relationType == "SERIES" && it.targetId == candidate.id && it.action == ManualOverrideAction.FORCE_REMOVE.name
            }
        } == true
        val canApply = automationMode == AutomationMode.AUTOMATIC && existingSeries != null && suggestion.confidence >= 90 && !blocked
        val disabled = automationMode == AutomationMode.DISABLED
        val status = when {
            disabled -> ProcessingStatus.PENDING
            canApply -> ProcessingStatus.ORGANIZED
            else -> ProcessingStatus.NEEDS_REVIEW
        }
        books.metadata(
            id = book.id,
            title = metadata?.title ?: book.title,
            author = metadata?.authors?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: suggestion.suggestedAuthor ?: book.author,
            contentType = (suggestion.suggestedContentType ?: runCatching { ContentType.valueOf(book.contentType) }.getOrDefault(ContentType.DOCUMENT)).name,
            publisher = metadata?.publisher ?: book.publisher,
            isbn = metadata?.isbn ?: book.isbn,
            year = metadata?.year ?: book.year,
        )
        books.organize(
            id = book.id,
            title = metadata?.title ?: book.title,
            fileHash = fileHash,
            seriesId = existingSeries?.id?.takeIf { canApply },
            volume = suggestion.volume,
            number = suggestion.number,
            publicationType = suggestion.publicationType.name,
            year = metadata?.year,
            status = status.name,
        )
        val ruleWarnings = applyRules(book, suggestion, metadata, existingSeries?.id?.takeIf { canApply })
        organization.insertImportItem(
            ImportItemEntity(
                sessionId = sessionId,
                bookId = book.id,
                status = status.name,
                detectedSeries = suggestion.detectedSeries,
                confidence = suggestion.confidence,
                requiresReview = !canApply && !disabled,
            ),
        )
        return suggestion.copy(requiresConfirmation = !canApply && !disabled, warnings = suggestion.warnings + ruleWarnings)
    }

    /** Creating a series and assigning a whole detected group is always an explicit user action. */
    suspend fun approveGroup(group: OrganizationReviewGroup): Result<Long> = runCatching {
        val name = group.seriesName?.trim().orEmpty()
        require(name.isNotBlank()) { "Informe a série antes de aprovar o grupo." }
        val firstBook = group.bookIds.firstOrNull()?.let { books.get(it) }
        val target = series.find(name)?.id ?: series.insert(
            SeriesEntity(canonicalName = name, displayName = name, year = firstBook?.year, publisher = firstBook?.publisher, createdAt = System.currentTimeMillis()),
        )
        group.suggestedCollectionId?.let { collectionId ->
            addRelation(collectionId, OrganizationChildType.SERIES, target, manual = true).getOrThrow()
        }
        group.bookIds.forEach { bookId ->
            val book = requireNotNull(books.get(bookId)) { "Item importado não encontrado." }
            books.organize(book.id, book.title, book.fileHash, target, book.volume, book.number, book.publicationType, book.year, ProcessingStatus.ORGANIZED.name)
            organization.saveOverride(ManualOverrideEntity(entityType = "BOOK", entityId = bookId, relationType = "SERIES", targetId = target, action = ManualOverrideAction.FORCE_ADD.name, createdAt = System.currentTimeMillis()))
        }
        group.importItemIds.forEach { organization.updateImportItem(it, ProcessingStatus.ORGANIZED.name, false) }
        firstBook?.let { applyRules(it, OrganizationEngine.analyze(it.id, it.title, null).copy(detectedSeries = name), null, target) }
        target
    }

    suspend fun removeBookFromSeries(bookId: Long): Result<Unit> = runCatching {
        val book = requireNotNull(books.get(bookId)) { "Livro não encontrado." }
        val oldSeries = book.seriesId ?: return@runCatching
        organization.saveOverride(ManualOverrideEntity(entityType = "BOOK", entityId = bookId, relationType = "SERIES", targetId = oldSeries, action = ManualOverrideAction.FORCE_REMOVE.name, createdAt = System.currentTimeMillis()))
        books.organize(book.id, book.title, book.fileHash, null, book.volume, book.number, book.publicationType, book.year, ProcessingStatus.NEEDS_REVIEW.name)
    }

    suspend fun addBooksToSeries(seriesId: Long, bookIds: Set<Long>): Result<Unit> = runCatching {
        require(series.get(seriesId) != null) { "Série não encontrada." }
        require(bookIds.isNotEmpty()) { "Selecione pelo menos um livro." }
        bookIds.forEach { bookId ->
            val book = requireNotNull(books.get(bookId)) { "Livro não encontrado." }
            book.seriesId?.takeIf { it != seriesId }?.let { previousSeriesId ->
                organization.saveOverride(
                    ManualOverrideEntity(
                        entityType = OrganizationChildType.BOOK.name,
                        entityId = bookId,
                        relationType = "SERIES",
                        targetId = previousSeriesId,
                        action = ManualOverrideAction.FORCE_REMOVE.name,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
            books.organize(
                id = book.id,
                title = book.title,
                fileHash = book.fileHash,
                seriesId = seriesId,
                volume = book.volume,
                number = book.number,
                publicationType = book.publicationType,
                year = book.year,
                status = ProcessingStatus.ORGANIZED.name,
            )
            organization.saveOverride(
                ManualOverrideEntity(
                    entityType = OrganizationChildType.BOOK.name,
                    entityId = bookId,
                    relationType = "SERIES",
                    targetId = seriesId,
                    action = ManualOverrideAction.FORCE_ADD.name,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun setSeriesInCollection(collectionId: Long, seriesIds: Set<Long>): Result<Unit> = runCatching {
        require(collections.get(collectionId) != null) { "Coleção não encontrada." }
        val currentIds = organization.relations()
            .filter { it.parentCollectionId == collectionId && it.childType == OrganizationChildType.SERIES.name }
            .mapTo(mutableSetOf()) { it.childId }
        (currentIds - seriesIds).forEach { removeRelation(collectionId, OrganizationChildType.SERIES, it) }
        (seriesIds - currentIds).forEach { addRelation(collectionId, OrganizationChildType.SERIES, it, manual = true).getOrThrow() }
    }

    suspend fun addRelation(parentCollectionId: Long, childType: OrganizationChildType, childId: Long, manual: Boolean = true): Result<Unit> = runCatching {
        require(collections.get(parentCollectionId) != null) { "Coleção pai não encontrada." }
        when (childType) {
            OrganizationChildType.COLLECTION -> require(collections.get(childId) != null) { "Coleção filha não encontrada." }
            OrganizationChildType.SERIES -> require(series.get(childId) != null) { "Série não encontrada." }
            OrganizationChildType.BOOK -> require(books.get(childId) != null) { "Livro não encontrado." }
        }
        val candidate = CollectionRelationEntity(parentCollectionId, childType.name, childId, System.currentTimeMillis())
        validateGraph(candidate)
        if (!manual) {
            val removed = organization.overrides(childType.name, childId).any {
                it.relationType == "COLLECTION" && it.targetId == parentCollectionId && it.action == ManualOverrideAction.FORCE_REMOVE.name
            }
            require(!removed) { "Uma decisão manual impede esta associação." }
        }
        organization.insertRelation(candidate)
        if (childType == OrganizationChildType.BOOK) collections.insertRefs(listOf(BookCollectionCrossRef(childId, parentCollectionId)))
        if (manual) organization.saveOverride(
            ManualOverrideEntity(entityType = childType.name, entityId = childId, relationType = "COLLECTION", targetId = parentCollectionId, action = ManualOverrideAction.FORCE_ADD.name, createdAt = System.currentTimeMillis()),
        )
    }

    suspend fun removeRelation(parentCollectionId: Long, childType: OrganizationChildType, childId: Long) {
        organization.deleteRelation(parentCollectionId, childType.name, childId)
        if (childType == OrganizationChildType.BOOK) {
            // Legacy membership is kept in sync by replacing it from current relation parents.
            val parents = organization.relations().filter { it.childType == "BOOK" && it.childId == childId }.map { it.parentCollectionId }.toSet()
            collections.replaceCollectionsForBook(childId, parents)
        }
        organization.saveOverride(ManualOverrideEntity(entityType = childType.name, entityId = childId, relationType = "COLLECTION", targetId = parentCollectionId, action = ManualOverrideAction.FORCE_REMOVE.name, createdAt = System.currentTimeMillis()))
    }

    suspend fun reprocess(bookId: Long, fileName: String, metadata: PublicationMetadata?): OrganizationSuggestion {
        val book = requireNotNull(books.get(bookId)) { "Livro não encontrado." }
        val suggestion = OrganizationEngine.analyze(bookId, fileName, metadata)
        val target = suggestion.detectedSeries?.let { series.find(it) }
        val overrides = organization.overrides("BOOK", bookId)
        val forced = overrides.lastOrNull { it.relationType == "SERIES" && it.action == ManualOverrideAction.FORCE_ADD.name }
        val blocked = target != null && overrides.any { it.relationType == "SERIES" && it.targetId == target.id && it.action == ManualOverrideAction.FORCE_REMOVE.name }
        val seriesId = forced?.targetId ?: target?.id?.takeUnless { blocked }
        books.metadata(
            id = book.id,
            title = metadata?.title ?: book.title,
            author = metadata?.authors?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: suggestion.suggestedAuthor ?: book.author,
            contentType = (suggestion.suggestedContentType ?: runCatching { ContentType.valueOf(book.contentType) }.getOrDefault(ContentType.DOCUMENT)).name,
            publisher = metadata?.publisher ?: book.publisher,
            isbn = metadata?.isbn ?: book.isbn,
            year = metadata?.year ?: book.year,
        )
        books.organize(book.id, metadata?.title ?: book.title, book.fileHash, seriesId, suggestion.volume, suggestion.number, suggestion.publicationType.name, metadata?.year ?: book.year, if (seriesId == null) ProcessingStatus.NEEDS_REVIEW.name else ProcessingStatus.ORGANIZED.name)
        val warnings = applyRules(book, suggestion, metadata, seriesId)
        return suggestion.copy(requiresConfirmation = seriesId == null, warnings = suggestion.warnings + warnings)
    }

    suspend fun seriesBookIds(seriesId: Long) = books.bySeries(seriesId).map { it.id }
    suspend fun allBookIds() = books.all().map { it.id }

    suspend fun missingNumbers(seriesId: Long) = OrganizationEngine.possibleMissingNumbers(books.bySeries(seriesId).filter { it.publicationType == "NORMAL" }.map { it.number })

    private suspend fun applyRules(
        book: BookEntity,
        suggestion: OrganizationSuggestion,
        metadata: PublicationMetadata?,
        seriesId: Long?,
    ): List<String> = buildList {
        val context = RuleEvaluationContext(
            title = metadata?.title ?: book.title,
            series = suggestion.detectedSeries,
            publisher = metadata?.publisher ?: book.publisher,
            format = book.format,
            contentType = suggestion.suggestedContentType ?: runCatching { ContentType.valueOf(book.contentType) }.getOrDefault(ContentType.DOCUMENT),
            author = metadata?.authors?.joinToString(", ") ?: suggestion.suggestedAuthor ?: book.author,
            isbn = metadata?.isbn ?: book.isbn,
            year = metadata?.year ?: book.year,
            publicationType = suggestion.publicationType.name,
            sourceReference = book.uri,
            isImport = book.processingStatus in setOf(ProcessingStatus.PENDING.name, ProcessingStatus.PROCESSING.name),
        )
        organization.enabledAdvancedRules().mapNotNull { it.toDomainOrNull() }.forEach { rule ->
            if (!AdvancedRuleEngine.matches(rule, context)) return@forEach
            val targetsSeries = seriesId != null && rule.conditions.any { it.field == RuleField.SERIES }
            val childType = if (targetsSeries) OrganizationChildType.SERIES else OrganizationChildType.BOOK
            val childId = if (targetsSeries) seriesId!! else book.id
            rule.actions.forEach { action ->
                runCatching {
                    when (action.type) {
                        RuleActionType.ADD_TO_COLLECTION -> addRelation(requireNotNull(action.targetCollectionId), childType, childId, manual = false).getOrThrow()
                        RuleActionType.REMOVE_FROM_COLLECTION -> removeRelationByRule(requireNotNull(action.targetCollectionId), childType, childId)
                        RuleActionType.CREATE_COLLECTION -> {
                            val name = requireNotNull(action.collectionName).trim()
                            val target = collections.find(name)?.id ?: run {
                                val now = System.currentTimeMillis()
                                collections.insert(com.arthur.ereader.data.local.CollectionEntity(name = name, description = "Criada pela regra ${rule.name}", color = CollectionColor.BLUE.name, createdAt = now, updatedAt = now))
                            }
                            addRelation(target, childType, childId, manual = false).getOrThrow()
                        }
                    }
                }.exceptionOrNull()?.let { add("Regra '${rule.name}' ignorada: ${it.message}") }
            }
        }
    }

    private suspend fun removeRelationByRule(parentCollectionId: Long, childType: OrganizationChildType, childId: Long) {
        val forced = organization.overrides(childType.name, childId).any {
            it.relationType == "COLLECTION" && it.targetId == parentCollectionId && it.action == ManualOverrideAction.FORCE_ADD.name
        }
        require(!forced) { "Uma decisão manual preserva esta associação." }
        organization.deleteRelation(parentCollectionId, childType.name, childId)
        if (childType == OrganizationChildType.BOOK) {
            val parents = organization.relations().filter { it.childType == "BOOK" && it.childId == childId }.map { it.parentCollectionId }.toSet()
            collections.replaceCollectionsForBook(childId, parents)
        }
    }

    private suspend fun validateGraph(candidate: CollectionRelationEntity) {
        val edges = organization.relations().toMutableList().apply { add(candidate) }.map {
            HierarchyEdge(
                parentCollectionId = it.parentCollectionId,
                childType = runCatching { OrganizationChildType.valueOf(it.childType) }.getOrDefault(OrganizationChildType.BOOK),
                childId = it.childId,
            )
        }
        HierarchyValidator.validate(edges, MAX_DEPTH)
    }

    companion object { const val MAX_DEPTH = 8 }
}

private fun com.arthur.ereader.data.local.AdvancedRuleWithParts.toDomainOrNull(): AdvancedOrganizationRule? = runCatching {
    AdvancedOrganizationRule(
        id = rule.id,
        name = rule.name,
        scope = RuleScope.valueOf(rule.scope),
        scopeValue = rule.scopeValue,
        priority = rule.priority,
        enabled = rule.enabled,
        conditions = conditions.map { RuleCondition(it.id, RuleField.valueOf(it.field), RuleMatch.valueOf(it.match), it.value) },
        actions = actions.map { RuleAction(it.id, RuleActionType.valueOf(it.actionType), it.targetCollectionId, it.collectionName) },
        createdAt = rule.createdAt,
    )
}.getOrNull()
