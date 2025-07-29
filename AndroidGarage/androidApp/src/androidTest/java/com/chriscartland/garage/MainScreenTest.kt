package com.chriscartland.garage

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chriscartland.garage.ui.Screen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testTopAppBarIsDisplayed() {
        composeTestRule.onNodeWithText("Garage").assertIsDisplayed()
    }

    @Test
    fun testBottomNavigationBarIsDisplayed() {
        composeTestRule.onNodeWithText(Screen.Home.label).assertIsDisplayed()
        composeTestRule.onNodeWithText(Screen.History.label).assertIsDisplayed()
        composeTestRule.onNodeWithText(Screen.Profile.label).assertIsDisplayed()
    }
}