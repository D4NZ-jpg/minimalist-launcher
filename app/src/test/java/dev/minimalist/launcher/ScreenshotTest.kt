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
        results = visibleApps(all, emptySet(), setOf(app("Wallet").key, app("Timer").key, app("Notes").key), false),
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

    /** Both screens move during the transition: home leaves while the list arrives, from opposite sides of rest. */
    @Test fun bothScreensMoveWhileOpening() {
        var drawer by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { LauncherContent(base.copy(showDrawer = drawer, swipe = SwipeDir.Left), LauncherActions()) } }
        rule.mainClock.advanceTimeBy(1000)
        val rest = rule.onNodeWithText("Files").fetchSemanticsNode().positionInRoot.x
        drawer = true
        repeat(8) { rule.mainClock.advanceTimeByFrame(); rule.onAllNodesWithText("Files").fetchSemanticsNodes() }
        val xs = rule.onAllNodesWithText("Files").fetchSemanticsNodes().map { it.positionInRoot.x }
        assertEquals(2, xs.size)
        assertTrue("home should have moved left of rest ($rest): $xs", xs.min() < rest - 20)
        assertTrue("list should still be right of rest ($rest): $xs", xs.max() > rest + 20)
    }

    @Test fun openingAnimatesBetweenHomeAndList() = checkTransition(true)
    @Test fun closingAnimatesBetweenListAndHome() = checkTransition(false)

    /** Distance between the tops of two neighbouring rows (the row pitch, including padding). */
    private fun pitch(a: String, b: String) = rule.onNodeWithText(b).fetchSemanticsNode().positionInRoot.y - rule.onNodeWithText(a).fetchSemanticsNode().positionInRoot.y

    @Test fun dragReordersHomeRows() {
        var moved: Pair<String, Int>? = null
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { LauncherContent(base, LauncherActions(moveFavourite = { a, d -> moved = a.label to d })) } }
        val h = pitch("Messages", "Contacts")
        rule.onNodeWithText("Messages").performTouchInput { down(center); advanceEventTime(700); moveBy(Offset(0f, 2f)); moveBy(Offset(0f, h * 2.2f)); up() }
        assertEquals("Messages" to 2, moved)
    }

    @Test fun dragUpwardsMovesBackwards() {
        var moved: Pair<String, Int>? = null
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { LauncherContent(base, LauncherActions(moveFavourite = { a, d -> moved = a.label to d })) } }
        val h = pitch("Music", "Weather")
        rule.onNodeWithText("Music").performTouchInput { down(center); advanceEventTime(700); moveBy(Offset(0f, -2f)); moveBy(Offset(0f, -h * 1.1f)); up() }
        assertEquals("Music" to -1, moved)
    }

    @Test fun tapLaunchesAndLongPressWithoutMovingOpensSheet() {
        var launched: String? = null
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { LauncherContent(base, LauncherActions(launch = { launched = it.label })) } }
        rule.onNodeWithText("Calendar").performClick()
        assertEquals("Calendar", launched)
        rule.onNodeWithText("Calendar").performTouchInput { down(center); advanceEventTime(700); up() }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Show on home").fetchSemanticsNodes().isNotEmpty())
        assertEquals("Calendar", launched)   // releasing a long-press must not also launch the app
    }

    @Test fun dragMidScreenshot() {
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { LauncherContent(base, LauncherActions()) } }
        val h = pitch("Music", "Weather")
        rule.onNodeWithText("Music").performTouchInput { down(center); advanceEventTime(700); moveBy(Offset(0f, 2f)); moveBy(Offset(0f, h * 2.1f)) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/drag_row.png")
    }

    /** Screens for the folder flow: runs [steps] on the composed UI, then captures. */
    private fun foldShot(name: String, s: LauncherState, steps: () -> Unit = {}) {
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { LauncherContent(s, LauncherActions()) } }
        steps()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }
    private fun longPress(text: String) { rule.onNodeWithText(text).performTouchInput { down(center); advanceEventTime(700); up() }; rule.waitForIdle() }
    private val inDrawer get() = base.copy(showDrawer = true)

    @Test fun foldAppSheet() = foldShot("fold_app_sheet", inDrawer) { rule.onNodeWithText("Money").performClick(); longPress("Wallet") }
    @Test fun foldNewFolder() = foldShot("fold_new_folder", inDrawer) { rule.onNodeWithText("Money").performClick(); longPress("Wallet"); rule.onNodeWithText("+ New folder").performClick() }
    @Test fun foldFolderSheet() = foldShot("fold_folder_sheet", inDrawer) { longPress("Money") }
    @Test fun foldDrawerExpanded() = foldShot("fold_drawer_expanded", inDrawer) { rule.onNodeWithText("Money").performClick(); rule.onNodeWithText("Tools").performClick() }
    @Test fun foldHomeExpanded() = foldShot("fold_home_expanded", base) { rule.onNodeWithText("Tools").performClick() }
    @Test fun foldEmptyFolder() = foldShot("fold_empty", inDrawer.copy(folders = base.folders + FolderUi("Games", emptyList()))) { rule.onNodeWithText("Games").performClick() }

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
