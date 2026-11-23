package dev.minimalist.launcher

/** Apps shown in the main list: not hidden, and (unless the user opted in) not inside any folder. */
fun visibleApps(all: List<AppEntry>, hidden: Set<String>, foldered: Set<String>, showFoldered: Boolean) =
    all.filter { it.key !in hidden && (showFoldered || it.key !in foldered) }

data class FolderUi(val name: String, val apps: List<AppEntry>)

data class LauncherState(
    val time: String = "", val date: String = "", val battery: Int = 100, val charging: Boolean = false,
    val usage: String = "", val usageGranted: Boolean = true,
    val favourites: List<AppEntry> = emptyList(), val dock: List<AppEntry> = emptyList(),
    val folders: List<FolderUi> = emptyList(), val homeFolders: List<FolderUi> = emptyList(), val results: List<AppEntry> = emptyList(),
    val allApps: List<AppEntry> = emptyList(), val hiddenKeys: Set<String> = emptySet(), val hiddenApps: List<AppEntry> = emptyList(),
    val query: String = "", val showDrawer: Boolean = false, val showSettings: Boolean = false,
    val dnd: Boolean = false, val dndGranted: Boolean = false, val use24h: Boolean = false, val swipe: SwipeDir = SwipeDir.Up, val textSize: TextSize = TextSize.Medium, val showFolderedApps: Boolean = false, val showSwipeHint: Boolean = false,
    val showFolderCounts: Boolean = false, val isDefaultHome: Boolean = true, val showHomePrompt: Boolean = false,
)

class LauncherActions(
    val showDrawer: (Boolean) -> Unit = {}, val showSettings: (Boolean) -> Unit = {}, val query: (String) -> Unit = {},
    val launch: (AppEntry) -> Unit = {}, val toggleFavourite: (AppEntry) -> Unit = {}, val toggleDock: (AppEntry) -> Unit = {},
    val toggleHidden: (AppEntry) -> Unit = {}, val uninstall: (AppEntry) -> Unit = {},
    val toggleFolder: (AppEntry, String) -> Unit = { _, _ -> }, val newFolder: (AppEntry, String) -> Unit = { _, _ -> },
    val deleteFolder: (String) -> Unit = {}, val toggleDnd: () -> Unit = {},
    val openUsageAccess: () -> Unit = {}, val openDndAccess: () -> Unit = {}, val openDefaultLauncher: () -> Unit = {}, val dismissHomePrompt: () -> Unit = {}, val setShowFolderCounts: (Boolean) -> Unit = {},
    val set24h: (Boolean) -> Unit = {}, val setSwipe: (SwipeDir) -> Unit = {}, val moveFavourite: (AppEntry, Int) -> Unit = { _, _ -> },
    val toggleHomeFolder: (String) -> Unit = {}, val moveHomeFolder: (String, Int) -> Unit = { _, _ -> },
    val setShowFolderedApps: (Boolean) -> Unit = {}, val setShowSwipeHint: (Boolean) -> Unit = {}, val setTextSize: (TextSize) -> Unit = {},
)
