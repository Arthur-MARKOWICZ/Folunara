package com.arthur.ereader.domain.organization

import com.arthur.ereader.domain.model.AdvancedOrganizationRule
import com.arthur.ereader.domain.model.ContentType
import com.arthur.ereader.domain.model.RuleField
import com.arthur.ereader.domain.model.RuleMatch
import com.arthur.ereader.domain.model.RuleScope
import java.text.Normalizer

data class RuleEvaluationContext(
    val title: String,
    val series: String?,
    val publisher: String?,
    val format: String,
    val contentType: ContentType,
    val author: String?,
    val isbn: String?,
    val year: Int?,
    val publicationType: String,
    val sourceReference: String,
    val isImport: Boolean,
)

object AdvancedRuleEngine {
    fun validate(rule: AdvancedOrganizationRule) {
        require(rule.name.isNotBlank()) { "Informe o nome da regra." }
        require(rule.priority in -1000..1000) { "A prioridade deve ficar entre -1000 e 1000." }
        require(rule.conditions.isNotEmpty()) { "Adicione pelo menos uma condição." }
        require(rule.actions.isNotEmpty()) { "Adicione pelo menos uma ação." }
        if (rule.scope == RuleScope.FOLDER) require(!rule.scopeValue.isNullOrBlank()) { "Informe a pasta do escopo." }
        rule.conditions.forEach { condition ->
            require(condition.value.isNotBlank()) { "Condições precisam de um valor." }
            if (condition.match == RuleMatch.REGEX) runCatching { Regex(condition.value) }
                .getOrElse { throw IllegalArgumentException("Expressão regular inválida: ${condition.value}") }
            if (condition.match in setOf(RuleMatch.GREATER_OR_EQUAL, RuleMatch.LESS_OR_EQUAL)) {
                require(condition.field == RuleField.YEAR && condition.value.toIntOrNull() != null) {
                    "Comparações numéricas são suportadas para o campo Ano."
                }
            }
        }
        rule.actions.forEach { action ->
            when (action.type) {
                com.arthur.ereader.domain.model.RuleActionType.CREATE_COLLECTION -> require(!action.collectionName.isNullOrBlank()) { "Informe o nome da coleção a criar." }
                else -> require(action.targetCollectionId != null) { "Selecione a coleção da ação." }
            }
        }
    }

    fun matches(rule: AdvancedOrganizationRule, context: RuleEvaluationContext): Boolean {
        if (!rule.enabled || !scopeMatches(rule, context)) return false
        return rule.conditions.all { condition ->
            val actual = when (condition.field) {
                RuleField.SERIES -> context.series
                RuleField.PUBLISHER -> context.publisher
                RuleField.FORMAT -> context.format
                RuleField.CONTENT_TYPE -> context.contentType.name
                RuleField.AUTHOR -> context.author
                RuleField.TITLE -> context.title
                RuleField.ISBN -> context.isbn
                RuleField.YEAR -> context.year?.toString()
                RuleField.PUBLICATION_TYPE -> context.publicationType
            }.orEmpty()
            compare(actual, condition.value, condition.match)
        }
    }

    private fun scopeMatches(rule: AdvancedOrganizationRule, context: RuleEvaluationContext) = when (rule.scope) {
        RuleScope.LIBRARY -> true
        RuleScope.COMICS -> context.contentType == ContentType.COMIC
        RuleScope.MANGA -> context.contentType == ContentType.MANGA
        RuleScope.EPUB -> context.format.equals("EPUB", true)
        RuleScope.PDF -> context.format.equals("PDF", true)
        RuleScope.FOLDER -> context.sourceReference.token().contains(rule.scopeValue.orEmpty().token())
        RuleScope.IMPORT -> context.isImport
    }

    private fun compare(actual: String, expected: String, match: RuleMatch): Boolean {
        return when (match) {
            RuleMatch.EQUALS -> actual.token() == expected.token()
            RuleMatch.NOT_EQUALS -> actual.token() != expected.token()
            RuleMatch.CONTAINS -> actual.token().contains(expected.token())
            RuleMatch.STARTS_WITH -> actual.token().startsWith(expected.token())
            RuleMatch.REGEX -> Regex(expected, RegexOption.IGNORE_CASE).containsMatchIn(actual)
            RuleMatch.GREATER_OR_EQUAL -> (actual.toDoubleOrNull() ?: return false) >= (expected.toDoubleOrNull() ?: return false)
            RuleMatch.LESS_OR_EQUAL -> (actual.toDoubleOrNull() ?: return false) <= (expected.toDoubleOrNull() ?: return false)
        }
    }
}

private fun String.token() = Normalizer.normalize(trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .replace(Regex("[ _-]+"), " ")
    .lowercase()
