package dev.minimalist.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.graphics.asImageBitmap
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
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE0E0E0.toInt() }
        val hole = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF202020.toInt() }
        when (kind) {
            "Phone" -> { val p = android.graphics.Path().apply { moveTo(14f, 30f); quadTo(14f, 14f, 30f, 14f); lineTo(38f, 36f); lineTo(30f, 46f); quadTo(40f, 66f, 60f, 68f); lineTo(70f, 60f); lineTo(88f, 70f); quadTo(86f, 86f, 70f, 86f); quadTo(14f, 80f, 14f, 30f); close() }; c.drawPath(p, fill) }
            "Camera" -> { c.drawRoundRect(10f, 26f, 86f, 78f, 12f, 12f, fill); c.drawRect(32f, 16f, 56f, 28f, fill); c.drawCircle(48f, 52f, 16f, hole); c.drawCircle(48f, 52f, 9f, fill) }
            else -> { c.drawCircle(48f, 38f, 26f, fill); val p = android.graphics.Path().apply { moveTo(26f, 52f); lineTo(70f, 52f); lineTo(48f, 90f); close() }; c.drawPath(p, fill); c.drawCircle(48f, 38f, 10f, hole) }
        }
        return b.asImageBitmap()
    }

    private val all = listOf("Alarms", "Books", "Calculator", "Calendar", "Camera", "Wallet", "Clock", "Compass", "Files", "Maps", "Music", "Notes", "Phone", "Stopwatch", "Timer", "Weather", "Contacts", "Messages").map(::app)
    private val base = LauncherState(
        time = "10:43 AM", date = "Sunday, 22nd November", battery = 80, usage = "Today's Usage: 1h 34m",
        favourites = listOf("Messages", "Contacts", "Calendar", "Music", "Weather", "Compass", "Files").map(::app),
        dock = listOf("Phone", "Camera", "Maps").map { n -> app(n).also { it.icon = icon(n) } },
        folders = listOf(FolderUi("Money", listOf(app("Wallet"))), FolderUi("Tools", listOf(app("Timer"), app("Notes")))),
        results = all,
    )

    private fun shot(name: String, s: LauncherState) {
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { LauncherContent(s, LauncherActions()) } }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun home() = shot("home", base)
    @Test fun drawer() = shot("drawer", base.copy(showDrawer = true))
    @Test fun search() = shot("search", base.copy(showDrawer = true, query = "s", results = all.filter { it.label.contains("s", true) }))
    @Test fun settings() = shot("settings", base.copy(showSettings = true, usageGranted = false, hiddenApps = listOf(app("Timer"))))
}
