package com.nagram.usbbridge

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class NavigationSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeAndBottomNavigationAreVisible() {
        composeRule.onNodeWithText("Your storage").assertIsDisplayed()
        composeRule.onNodeWithText("Files").assertIsDisplayed()
        composeRule.onNodeWithText("Duplicates").assertIsDisplayed()
        composeRule.onNodeWithText("Transfer").assertIsDisplayed()
    }
}
