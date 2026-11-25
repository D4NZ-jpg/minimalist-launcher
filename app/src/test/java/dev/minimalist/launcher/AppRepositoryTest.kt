package dev.minimalist.launcher

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test fun declaresPermissionToRequestUninstall() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().contains(Manifest.permission.REQUEST_DELETE_PACKAGES))
    }

    @Test fun uninstallOpensSystemConfirmationForThePackage() {
        AppRepository(context).uninstall(AppEntry("Example", "dev.example.app", "dev.example.app.MainActivity"))

        val intent = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_DELETE, intent.action)
        assertEquals(Uri.parse("package:dev.example.app"), intent.data)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
