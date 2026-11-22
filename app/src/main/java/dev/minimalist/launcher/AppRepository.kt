package dev.minimalist.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(val label: String, val packageName: String, val className: String) {
    val key get() = "$packageName/$className"
    val component get() = ComponentName(packageName, className)
    var icon: ImageBitmap? = null
}

/** Launchable apps from PackageManager plus favourites/hidden sets stored in SharedPreferences. */
class AppRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun loadApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, 0)
            .map { AppEntry(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun favourites(): List<String> = prefs.getString("favs", "")!!.split("\n").filter { it.isNotEmpty() }
    fun hidden(): Set<String> = prefs.getStringSet("hidden", emptySet())!!.toSet()

    fun setFavourite(key: String, on: Boolean) {
        val list = favourites().toMutableList().apply { remove(key); if (on) add(key) }
        prefs.edit().putString("favs", list.joinToString("\n")).apply()
    }

    fun setHidden(key: String, on: Boolean) {
        val set = hidden().toMutableSet().apply { if (on) add(key) else remove(key) }
        prefs.edit().putStringSet("hidden", set).apply()
    }

    /** Dock keys. On first run, defaults to the apps that handle Dial and Camera. */
    fun dock(): List<String> {
        if (!prefs.contains("dock")) {
            val pm = context.packageManager
            val defaults = listOf(Intent(Intent.ACTION_DIAL), Intent(MediaStore.ACTION_IMAGE_CAPTURE)).mapNotNull {
                pm.resolveActivity(it, 0)?.activityInfo?.let { a -> a.packageName }
            }
            val keys = loadApps().filter { it.packageName in defaults }.map { it.key }
            prefs.edit().putString("dock", keys.joinToString("\n")).apply()
        }
        return prefs.getString("dock", "")!!.split("\n").filter { it.isNotEmpty() }
    }

    fun setDock(key: String, on: Boolean) {
        val list = dock().toMutableList().apply { remove(key); if (on && size < 5) add(key) }
        prefs.edit().putString("dock", list.joinToString("\n")).apply()
    }

    fun loadIcon(app: AppEntry) {
        if (app.icon == null) app.icon = runCatching {
            context.packageManager.getActivityIcon(app.component).toBitmap(128, 128).asImageBitmap()
        }.getOrNull()
    }

    var use24h: Boolean
        get() = prefs.getBoolean("use24h", false)
        set(v) { prefs.edit().putBoolean("use24h", v).apply() }

    fun folderNames(): List<String> = prefs.getString("folders", "")!!.split("\n").filter { it.isNotEmpty() }

    /** folder name -> member app keys */
    fun folderMembers(): Map<String, List<String>> =
        prefs.getString("folderMap", "")!!.split("\n").filter { it.contains('\t') }
            .map { it.split('\t', limit = 2) }.groupBy({ it[0] }, { it[1] })

    fun createFolder(name: String): String? {
        val n = cleanFolderName(name)
        if (n.isEmpty()) return null
        if (n !in folderNames()) prefs.edit().putString("folders", (folderNames() + n).joinToString("\n")).apply()
        return n
    }

    fun deleteFolder(name: String) {
        prefs.edit().putString("folders", folderNames().filter { it != name }.joinToString("\n")).apply()
        writeMap(folderMembers() - name)
    }

    /** An app can be in any number of folders. Adds it if absent, removes it if present. */
    fun toggleFolder(key: String, name: String) {
        val m = folderMembers().toMutableMap()
        val cur = m[name] ?: emptyList()
        m[name] = if (key in cur) cur - key else cur + key
        writeMap(m)
    }

    fun addToFolder(key: String, name: String) {
        val cur = folderMembers()[name] ?: emptyList()
        if (key !in cur) toggleFolder(key, name)
    }

    private fun writeMap(m: Map<String, List<String>>) {
        prefs.edit().putString("folderMap", m.entries.flatMap { (n, ks) -> ks.map { "$n\t$it" } }.joinToString("\n")).apply()
    }

    fun launch(app: AppEntry) {
        context.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        )
    }

    fun uninstall(app: AppEntry) {
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun cleanFolderName(n: String) = n.replace(Regex("\\s+"), " ").trim()
