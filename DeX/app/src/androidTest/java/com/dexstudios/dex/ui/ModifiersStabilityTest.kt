package com.dexstudios.dex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.ui.components.bubbleFluidity
import com.dexstudios.dex.ui.components.shake
import org.junit.Rule
import org.junit.Test

class ModifiersStabilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun bubbleFluidity_doesNotCrash_onInteraction() {
        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .testTag("bubble_box")
                    .bubbleFluidity()
            )
        }

        // Simulate touch interactions
        composeTestRule.onNodeWithTag("bubble_box")
            .performTouchInput {
                down(center)
                moveBy(Offset(10f, 10f))
                up()
            }

        composeTestRule.waitForIdle()
    }

    @Test
    fun shake_rendersAndTriggers() {
        var isError by mutableStateOf(false)
        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .shake(isError)
            )
        }

        // Toggle shake
        isError = true
        composeTestRule.waitForIdle()

        isError = false
        composeTestRule.waitForIdle()
    }


}
