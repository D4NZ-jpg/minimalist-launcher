package dev.minimalist.launcher

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [32, 34, 35])
class BackNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(LauncherState())
    private val navigation = mutableListOf<String>()

    private fun showLauncher() {
        rule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                LauncherContent(state, LauncherActions(
                    showDrawer = { state = state.copy(showDrawer = it); navigation += "drawer:$it" },
                    showSettings = { state = state.copy(showSettings = it); navigation += "settings:$it" },
                ))
            }
        }
        rule.waitForIdle()
    }

    private fun pressBack() {
        rule.runOnIdle {
            assertTrue("The launcher must always consume Back", rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
            rule.activity.onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()
        rule.runOnIdle { assertFalse(rule.activity.isFinishing) }
    }

    @Test fun repeatedBackOnHomeDoesNotNavigateOrFinish() {
        showLauncher()
        repeat(3) { pressBack() }
        assertEquals(LauncherState(), state)
        assertTrue(navigation.isEmpty())
    }

    @Test fun backClosesDrawerThenStaysOnHome() {
        state = state.copy(showDrawer = true)
        showLauncher()
        pressBack()
        assertFalse(state.showDrawer)
        pressBack()
        assertEquals(listOf("drawer:false"), navigation)
    }

    @Test fun backClosesSettingsBeforeDrawerThenStaysOnHome() {
        state = state.copy(showDrawer = true, showSettings = true)
        showLauncher()
        pressBack()
        assertFalse(state.showSettings)
        assertTrue(state.showDrawer)
        pressBack()
        assertFalse(state.showDrawer)
        pressBack()
        assertEquals(listOf("settings:false", "drawer:false"), navigation)
    }

    @Test fun backClosesSettingsSubpageBeforeSettings() {
        state = state.copy(showSettings = true)
        showLauncher()
        rule.onNodeWithText("Apps and folders").performClick()
        rule.onNodeWithText("Hide more apps…").performClick()
        pressBack()
        assertTrue(state.showSettings)
        assertTrue(navigation.isEmpty())
        pressBack()
        assertTrue(state.showSettings)
        assertTrue(navigation.isEmpty())
        pressBack()
        assertFalse(state.showSettings)
        pressBack()
        assertEquals(listOf("settings:false"), navigation)
    }
}
