package com.dexstudios.dex.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.dexstudios.dex.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
  @Before
  fun setup() {
    composeTestRule.setContent {
      val windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(400.dp, 800.dp))
      MainScreen(windowSizeClass = windowSizeClass)
    }
  }

  @Test
  fun myDevicesHeader_exists() {
    composeTestRule.onNodeWithText("My Devices").assertExists()
  }
}
