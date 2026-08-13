package com.arthur.ereader.domain.organization

import com.arthur.ereader.domain.model.AdvancedOrganizationRule
import com.arthur.ereader.domain.model.ContentType
import com.arthur.ereader.domain.model.RuleAction
import com.arthur.ereader.domain.model.RuleActionType
import com.arthur.ereader.domain.model.RuleCondition
import com.arthur.ereader.domain.model.RuleField
import com.arthur.ereader.domain.model.RuleMatch
import com.arthur.ereader.domain.model.RuleScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedRuleEngineTest {
    private val context = RuleEvaluationContext(
        title = "Batman: Ano Um", series = "Batman", publisher = "DC Comics", format = "PDF",
        contentType = ContentType.COMIC, author = "Frank Miller", isbn = "9781234567890", year = 1987,
        publicationType = "NORMAL", sourceReference = "content://imports/DC/Batman.pdf", isImport = true,
    )

    @Test fun matchesScopeAndEveryConditionIgnoringAccentsAndCase() {
        val rule = rule(
            scope = RuleScope.COMICS,
            conditions = listOf(
                RuleCondition(field = RuleField.PUBLISHER, match = RuleMatch.STARTS_WITH, value = "dc"),
                RuleCondition(field = RuleField.TITLE, match = RuleMatch.CONTAINS, value = "ano um"),
                RuleCondition(field = RuleField.YEAR, match = RuleMatch.GREATER_OR_EQUAL, value = "1980"),
            ),
        )
        assertTrue(AdvancedRuleEngine.matches(rule, context))
        assertFalse(AdvancedRuleEngine.matches(rule.copy(scope = RuleScope.EPUB), context))
    }

    @Test fun rejectsInvalidRegexAndNumericComparisonOnText() {
        assertTrue(runCatching { AdvancedRuleEngine.validate(rule(conditions = listOf(RuleCondition(field = RuleField.TITLE, match = RuleMatch.REGEX, value = "[")))) }.isFailure)
        assertTrue(runCatching { AdvancedRuleEngine.validate(rule(conditions = listOf(RuleCondition(field = RuleField.TITLE, match = RuleMatch.GREATER_OR_EQUAL, value = "2")))) }.isFailure)
    }

    @Test fun validatesMultipleActionsAndPriority() {
        val rule = rule().copy(
            priority = 100,
            actions = listOf(
                RuleAction(type = RuleActionType.ADD_TO_COLLECTION, targetCollectionId = 2),
                RuleAction(type = RuleActionType.CREATE_COLLECTION, collectionName = "Clássicos"),
            ),
        )
        AdvancedRuleEngine.validate(rule)
        assertTrue(runCatching { AdvancedRuleEngine.validate(rule.copy(priority = 1001)) }.isFailure)
    }

    private fun rule(
        scope: RuleScope = RuleScope.LIBRARY,
        conditions: List<RuleCondition> = listOf(RuleCondition(field = RuleField.SERIES, match = RuleMatch.EQUALS, value = "Batman")),
    ) = AdvancedOrganizationRule(
        name = "Organizar Batman", scope = scope, conditions = conditions,
        actions = listOf(RuleAction(type = RuleActionType.ADD_TO_COLLECTION, targetCollectionId = 1)),
    )
}
