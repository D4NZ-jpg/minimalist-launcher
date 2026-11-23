package dev.minimalist.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Direction of the swipe that opens the full app list. Left means right-to-left. */
/** Scales the clock and the app lists. */
enum class TextSize(val label: String, val scale: Float) { Small("Small", 0.85f), Medium("Medium", 1f), Large("Large", 1.2f) }

enum class SwipeDir(val label: String) { Up("Up"), Left("Right to left"), Right("Left to right") }

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
            val d = context.packageManager.getActivityIcon(app.component)
            if (d is AdaptiveIconDrawable) {
                // Adaptive icons are 108dp layers with the visible mask in the centre 72dp.
                // Crop to that region so the artwork fills a circular button edge to edge.
                val full = d.toBitmap(216, 216)
                Bitmap.createBitmap(full, 36, 36, 144, 144).asImageBitmap()
            } else d.toBitmap(128, 128).asImageBitmap()
        }.getOrNull()
    }

    var use24h: Boolean
        get() = prefs.getBoolean("use24h", false)
        set(v) { prefs.edit().putBoolean("use24h", v).apply() }

    var swipe: SwipeDir
        get() = SwipeDir.entries.firstOrNull { it.name == prefs.getString("swipe", null) } ?: SwipeDir.Up
        set(v) { prefs.edit().putString("swipe", v.name).apply() }

    fun replaceFavourites(keys: List<String>) { prefs.edit().putString("favs", keys.distinct().joinToString("\n")).apply() }
    fun replaceDock(keys: List<String>) { prefs.edit().putString("dock", keys.distinct().take(5).joinToString("\n")).apply() }
    fun replaceHidden(keys: Collection<String>) { prefs.edit().putStringSet("hidden", keys.toSet()).apply() }

    /** Replaces every folder. Folder order follows the map's iteration order. */
    fun replaceFolders(folders: Map<String, List<String>>) {
        val cleaned = folders.mapKeys { cleanFolderName(it.key) }.filterKeys { it.isNotEmpty() }
        prefs.edit().putString("folders", cleaned.keys.joinToString("\n")).apply()
        writeMap(cleaned.mapValues { it.value.distinct() })
    }

    fun registerChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(l)
    fun unregisterChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(l)

    var textSize: TextSize
        get() = TextSize.entries.firstOrNull { it.name == prefs.getString("textSize", null) } ?: TextSize.Medium
        set(v) { prefs.edit().putString("textSize", v.name).apply() }

    /** Folders chosen to appear on the home screen, in display order. */
    fun homeFolders(): List<String> = prefs.getString("homeFolders", "")!!.split("\n").filter { it.isNotEmpty() }
    fun replaceHomeFolders(names: List<String>) { prefs.edit().putString("homeFolders", names.distinct().joinToString("\n")).apply() }
    fun setHomeFolder(name: String, on: Boolean) = replaceHomeFolders(homeFolders().toMutableList().apply { remove(name); if (on) add(name) })

    fun moveFavourite(key: String, delta: Int) = replaceFavourites(moved(favourites(), key, delta))
    fun moveHomeFolder(name: String, delta: Int) = replaceHomeFolders(moved(homeFolders(), name, delta))
    private fun moved(list: List<String>, item: String, delta: Int): List<String> {
        val i = list.indexOf(item); if (i < 0) return list
        return list.toMutableList().apply { add((i + delta).coerceIn(0, size - 1), removeAt(i)) }
    }

    /** When off, apps that sit in a folder are left out of the main app list (search still finds them). */
    var showFolderedApps: Boolean
        get() = prefs.getBoolean("showFoldered", false)
        set(v) { prefs.edit().putBoolean("showFoldered", v).apply() }

    var showSwipeHint: Boolean
        get() = prefs.getBoolean("showHint", false)
        set(v) { prefs.edit().putBoolean("showHint", v).apply() }

    /** Set once the first-launch "make this your home app" prompt has been shown. */
    var askedHomePrompt: Boolean
        get() = prefs.getBoolean("askedHome", false)
        set(v) { prefs.edit().putBoolean("askedHome", v).apply() }

    var showFolderCounts: Boolean
        get() = prefs.getBoolean("folderCounts", false)
        set(v) { prefs.edit().putBoolean("folderCounts", v).apply() }

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
        setHomeFolder(name, false)
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
