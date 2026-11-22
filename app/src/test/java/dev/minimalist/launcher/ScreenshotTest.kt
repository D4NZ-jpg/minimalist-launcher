package dev.minimalist.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun app(n: String) = AppEntry(n, "pkg.${n.lowercase().replace(" ", "")}", "Main")
    /** Simple distinct glyphs standing in for real app icons. */
    private fun icon(kind: String): androidx.compose.ui.graphics.ImageBitmap {
        val b = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        c.drawColor(0xFFD8D8D8.toInt())   // icons now fill the whole circle, so give the fake ones a full-bleed background
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF202020.toInt() }
        val hole = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD8D8D8.toInt() }
        when (kind) {
            "Phone" -> { val p = android.graphics.Path().apply { moveTo(14f, 30f); quadTo(14f, 14f, 30f, 14f); lineTo(38f, 36f); lineTo(30f, 46f); quadTo(40f, 66f, 60f, 68f); lineTo(70f, 60f); lineTo(88f, 70f); quadTo(86f, 86f, 70f, 86f); quadTo(14f, 80f, 14f, 30f); close() }; c.drawPath(p, fill) }
            "Camera" -> { c.drawRoundRect(10f, 26f, 86f, 78f, 12f, 12f, fill); c.drawRect(32f, 16f, 56f, 28f, fill); c.drawCircle(48f, 52f, 16f, hole); c.drawCircle(48f, 52f, 9f, fill) }
            else -> { c.drawCircle(48f, 38f, 26f, fill); val p = android.graphics.Path().apply { moveTo(26f, 52f); lineTo(70f, 52f); lineTo(48f, 90f); close() }; c.drawPath(p, fill); c.drawCircle(48f, 38f, 10f, hole) }
        }
        return b.asImageBitmap()
    }

    private val all = listOf("Alarms", "Books", "Calculator", "Calendar", "Camera", "Wallet", "Clock", "Compass", "Files", "Maps", "Music", "Notes", "Podcasts", "Phone", "Photos", "Recorder", "Stopwatch", "Timer", "Translate", "Weather", "Tasks", "Contacts", "Messages", "Videos").map(::app)
    private val base = LauncherState(
        time = "10:43 AM", date = "Sunday, 22nd November", battery = 80, usage = "Today's Usage: 1h 34m",
        favourites = listOf("Messages", "Contacts", "Calendar", "Music", "Weather", "Compass", "Files").map(::app),
        dock = listOf("Phone", "Camera", "Maps").map { n -> app(n).also { it.icon = icon(n) } },
        folders = listOf(FolderUi("Money", listOf(app("Wallet"))), FolderUi("Tools", listOf(app("Timer"), app("Notes")))),
        homeFolders = listOf(FolderUi("Tools", listOf(app("Timer"), app("Notes")))),
        results = all,
    )

    private fun shot(name: String, s: LauncherState, pick: Boolean = false) {
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { LauncherContent(s, LauncherActions()) } }
        if (pick) { rule.onNodeWithText("Hide more apps…").performClick(); rule.waitForIdle() }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun shotDrag(name: String, s: LauncherState, frac: Float, startX: Float, wanderX: Float) {
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { LauncherContent(s, LauncherActions()) } }
        rule.onNodeWithTag("alphabet").performTouchInput {
            down(Offset(startX, height * 0.05f))
            moveTo(Offset(startX + wanderX, height * frac))   // finger slides down (and sideways), still held
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun dragMid() = shotDrag("drag_mid", base.copy(showDrawer = true), 0.45f, startX = 24f, wanderX = 0f)
    @Test fun dragLow() = shotDrag("drag_low", base.copy(showDrawer = true), 0.85f, startX = 24f, wanderX = 0f)
    @Test fun dragLeftStart() = shotDrag("drag_left_start", base.copy(showDrawer = true), 0.6f, startX = 2f, wanderX = 0f)
    @Test fun dragWander() = shotDrag("drag_wander", base.copy(showDrawer = true), 0.6f, startX = 24f, wanderX = -150f)

    private fun shotLongPress(name: String, s: LauncherState, text: String) {
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { LauncherContent(s, LauncherActions()) } }
        rule.onNodeWithText(text).performTouchInput { longClick() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    /** While the app list opens or closes, home and the list are both on screen; afterwards only one is. */
    private fun checkTransition(opening: Boolean) {
        var drawer by mutableStateOf(!opening)
        rule.mainClock.autoAdvance = false
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { LauncherContent(base.copy(showDrawer = drawer, swipe = SwipeDir.Left), LauncherActions()) } }
        rule.mainClock.advanceTimeBy(1000)
        drawer = opening
        // Querying between frames lets the test clock apply the pending composition.
        repeat(8) { rule.mainClock.advanceTimeByFrame(); rule.onAllNodesWithText("Files").fetchSemanticsNodes() }
        // Mid-transition both screens are composed: "Files" is on home and in the list, "Search apps" only in the list.
        assertTrue(rule.onAllNodesWithText("Search apps").fetchSemanticsNodes().isNotEmpty())
        assertEquals(2, rule.onAllNodesWithText("Files").fetchSemanticsNodes().size)
        rule.mainClock.advanceTimeBy(1000)
        if (opening) assertEquals(1, rule.onAllNodesWithText("Files").fetchSemanticsNodes().size) else assertTrue(rule.onAllNodesWithText("Search apps").fetchSemanticsNodes().isEmpty())
    }

    @Test fun openingAnimatesBetweenHomeAndList() = checkTransition(true)
    @Test fun closingAnimatesBetweenListAndHome() = checkTransition(false)

    @Test fun sheetApp() = shotLongPress("sheet_app", base, "Calendar")
    @Test fun sheetFolder() = shotLongPress("sheet_folder", base.copy(showDrawer = true), "Money")

    @Test fun home() = shot("home", base)
    @Test fun homeSmall() = shot("home_small", base.copy(textSize = TextSize.Small))
    @Test fun homeLarge() = shot("home_large", base.copy(textSize = TextSize.Large))
    @Test fun homeSwipeLeft() = shot("home_swipe_left", base.copy(swipe = SwipeDir.Left))
    @Test fun drawer() = shot("drawer", base.copy(showDrawer = true))
    @Test fun search() = shot("search", base.copy(showDrawer = true, query = "s", results = all.filter { it.label.contains("s", true) }))
    @Test fun settings() = shot("settings", base.copy(showSettings = true, usageGranted = false, hiddenApps = listOf(app("Timer"))))
    @Test fun hidePicker() = shot("hide_picker", base.copy(showSettings = true, allApps = all, hiddenKeys = setOf(app("Timer").key)), pick = true)
}
