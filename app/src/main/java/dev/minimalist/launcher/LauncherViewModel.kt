package dev.minimalist.launcher

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

class LauncherViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private var all: List<AppEntry> = emptyList()

    var favourites by mutableStateOf<List<AppEntry>>(emptyList()); private set
    var dock by mutableStateOf<List<AppEntry>>(emptyList()); private set
    var folders by mutableStateOf<List<FolderUi>>(emptyList()); private set
    var homeFolders by mutableStateOf<List<FolderUi>>(emptyList()); private set
    private var folderedKeys: Set<String> = emptySet()
    var allApps by mutableStateOf<List<AppEntry>>(emptyList()); private set
    var results by mutableStateOf<List<AppEntry>>(emptyList()); private set
    var hiddenKeys by mutableStateOf<Set<String>>(emptySet()); private set
    var hiddenApps by mutableStateOf<List<AppEntry>>(emptyList()); private set
    var query by mutableStateOf(""); private set
    var showDrawer by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var use24h by mutableStateOf(repo.use24h); private set
    var swipe by mutableStateOf(repo.swipe); private set
    var textSize by mutableStateOf(repo.textSize); private set
    var showFolderedApps by mutableStateOf(repo.showFolderedApps); private set
    var showSwipeHint by mutableStateOf(repo.showSwipeHint); private set
    var showFolderCounts by mutableStateOf(repo.showFolderCounts); private set
    var usageMinutes by mutableStateOf<Long?>(null); private set
    var usageGranted by mutableStateOf(false); private set
    var dndGranted by mutableStateOf(false); private set
    var dnd by mutableStateOf(false); private set
    var isDefaultHome by mutableStateOf(true); private set
    var askedHomePrompt by mutableStateOf(repo.askedHomePrompt); private set

    // Reload when something else (the ADB agent receiver) changes the stored layout.
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val reload = Runnable { refresh() }
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.removeCallbacks(reload); handler.postDelayed(reload, 150)
    }

    init { refresh(); refreshSystem(); repo.registerChangeListener(prefsListener) }

    override fun onCleared() { repo.unregisterChangeListener(prefsListener); handler.removeCallbacks(reload) }

    fun refresh() {
        all = repo.loadApps()
        hiddenKeys = repo.hidden()
        val byKey = all.associateBy { it.key }
        favourites = repo.favourites().mapNotNull { byKey[it] }
        dock = repo.dock().mapNotNull { byKey[it] }.onEach(repo::loadIcon)
        val members = repo.folderMembers()
        folders = repo.folderNames().map { n -> FolderUi(n, (members[n] ?: emptyList()).mapNotNull { byKey[it] }.sortedBy { it.label.lowercase() }) }
        homeFolders = repo.homeFolders().mapNotNull { n -> folders.firstOrNull { it.name == n } }
        folderedKeys = members.values.flatten().toSet()
        allApps = all
        hiddenApps = all.filter { it.key in hiddenKeys }
        updateResults()
    }

    fun refreshSystem() {
        usageGranted = SystemInfo.hasUsageAccess(app)
        usageMinutes = if (usageGranted) runCatching { SystemInfo.usageTodayMinutes(app) }.getOrNull() else null
        isDefaultHome = SystemInfo.isDefaultHome(app)
        dndGranted = SystemInfo.hasDndAccess(app)
        dnd = dndGranted && SystemInfo.isDndOn(app)
    }

    fun onQuery(q: String) { query = q; updateResults() }

    private fun updateResults() {
        val q = query.trim().lowercase()
        results = if (q.isEmpty()) visibleApps(all, hiddenKeys, folderedKeys, showFolderedApps)
        else all.filter { it.label.lowercase().contains(q) }
            .sortedWith(compareBy({ !it.label.lowercase().startsWith(q) }, { it.label.lowercase() }))
    }

    fun launch(a: AppEntry) { repo.launch(a); query = ""; showDrawer = false; updateResults() }
    fun toggleFavourite(a: AppEntry) { repo.setFavourite(a.key, a.key !in favourites.map { it.key }); refresh() }
    fun toggleDock(a: AppEntry) { repo.setDock(a.key, a.key !in dock.map { it.key }); refresh() }
    fun toggleHidden(a: AppEntry) { repo.setHidden(a.key, a.key !in hiddenKeys); refresh() }
    fun uninstall(a: AppEntry) = repo.uninstall(a)
    fun toggleFolder(a: AppEntry, name: String) { repo.toggleFolder(a.key, name); refresh() }
    fun newFolder(a: AppEntry, name: String) { repo.createFolder(name)?.let { repo.addToFolder(a.key, it) }; refresh() }
    fun deleteFolder(name: String) { repo.deleteFolder(name); refresh() }
    fun moveFavourite(a: AppEntry, delta: Int) { repo.moveFavourite(a.key, delta); refresh() }
    fun toggleHomeFolder(name: String) { repo.setHomeFolder(name, name !in homeFolders.map { it.name }); refresh() }
    fun moveHomeFolder(name: String, delta: Int) { repo.moveHomeFolder(name, delta); refresh() }
    fun chooseShowFoldered(on: Boolean) { repo.showFolderedApps = on; showFolderedApps = on; updateResults() }
    fun chooseShowFolderCounts(on: Boolean) { repo.showFolderCounts = on; showFolderCounts = on }
    fun chooseShowHint(on: Boolean) { repo.showSwipeHint = on; showSwipeHint = on }
    fun chooseTextSize(t: TextSize) { repo.textSize = t; textSize = t }
    fun chooseSwipe(d: SwipeDir) { repo.swipe = d; swipe = d }
    fun set24h(on: Boolean) { repo.use24h = on; use24h = on }

    fun toggleDnd() {
        if (!dndGranted) { openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS); return }
        SystemInfo.setDnd(app, !dnd); dnd = !dnd
    }

    fun openUsageAccess() = openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    fun openDndAccess() = openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    fun dismissHomePrompt() { repo.askedHomePrompt = true; askedHomePrompt = true }
    private fun openSettings(action: String) =
        app.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
