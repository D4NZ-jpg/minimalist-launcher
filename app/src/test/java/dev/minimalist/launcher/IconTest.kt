package dev.minimalist.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class IconTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun icon() = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable

    @Test fun launcherIconIsAnAdaptiveIconWithAllThreeLayers() {
        val d = icon()
        assertNotNull(d.background); assertNotNull(d.foreground)
        assertNotNull("themed (monochrome) layer", d.monochrome)
    }

    @Test fun theRoundIconIsTheSameAdaptiveIcon() {
        assertTrue(context.getDrawable(R.mipmap.ic_launcher_round) is AdaptiveIconDrawable)
    }

    @Test fun theMarkStaysInsideTheAdaptiveSafeZone() {
        // The visible circle of a 108dp adaptive icon has a 33dp radius. Render the foreground and check
        // that every lit pixel lies inside it.
        val size = 432
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val fg = icon().foreground
        fg.setBounds(0, 0, size, size); fg.draw(Canvas(bmp))
        val c = size / 2f; val limit = 33f / 108f * size
        var lit = 0
        for (y in 0 until size) for (x in 0 until size) {
            if ((bmp.getPixel(x, y) ushr 24) > 40) {
                lit++
                val d = kotlin.math.hypot(x - c, y - c)
                assertTrue("pixel at ($x,$y) is ${d} from the centre, beyond $limit", d <= limit)
            }
        }
        assertTrue("the mark should be drawn", lit > 10_000)
    }

    @Test fun theCutLeavesAGapThroughTheMiddle() {
        val size = 432
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val fg = icon().foreground
        fg.setBounds(0, 0, size, size); fg.draw(Canvas(bmp))
        val col = size / 2
        // walking down the centre column: lit, then a gap, then lit again
        var runs = 0; var prevLit = false
        for (y in 0 until size) { val lit = (bmp.getPixel(col, y) ushr 24) > 128; if (lit && !prevLit) runs++; prevLit = lit }
        assertEquals("the sun is cut into two pieces", 2, runs)
    }

    @Test fun renderPreview() {
        val size = 432
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val d = icon(); d.setBounds(0, 0, size, size); d.draw(Canvas(bmp))
        bmp.captureRoboImage("build/screenshots/app_icon.png")
    }
}
