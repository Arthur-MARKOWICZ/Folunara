package com.arthur.ereader.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.pressBack
import com.arthur.ereader.MainActivity
import org.junit.Rule
import org.junit.Test

class AppNavigationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun drawerNavigatesToSettingsAndAndroidBackReturnsHome() {
        compose.onNodeWithContentDescription("Abrir menu").performClick()
        compose.onNodeWithText("Configurações").performClick()
        compose.onAllNodesWithText("Configurações").assertAny(hasText("Configurações"))

        pressBack()

        compose.onAllNodesWithText("Início").assertAny(hasText("Início"))
    }

    @Test
    fun edgeSwipeOpensDrawerAndBackClosesIt() {
        compose.onRoot().performTouchInput {
            swipe(Offset(1f, centerY), Offset(width * 0.8f, centerY), 500)
        }
        compose.onAllNodesWithText("Biblioteca").assertAny(hasText("Biblioteca"))

        pressBack()

        compose.waitUntil(2_000) {
            compose.onAllNodesWithText("Sobre").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithContentDescription("Abrir menu").assertIsDisplayed()
    }
}
