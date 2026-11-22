package dev.minimalist.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val Bg = Color(0xFF0B0B0B)
private val LocalTextScale = compositionLocalOf { 1f }
@Composable private fun sz(v: Int) = (v * LocalTextScale.current).sp

private val Dim = Color.White.copy(alpha = 0.7f)

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { LauncherScreen(vm) } }
    }

    override fun onResume() { super.onResume(); vm.refresh(); vm.refreshSystem() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) { vm.showDrawer = false; vm.showSettings = false }
    }
}

private fun ordinal(d: Int) = if (d in 11..13) "th" else when (d % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }

@Composable
fun LauncherScreen(vm: LauncherViewModel) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) { now = LocalDateTime.now(); vm.refreshSystem(); delay(60_000L - now.second * 1000L) }
    }

    var battery by remember { mutableIntStateOf(100) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                battery = level * 100 / scale
                charging = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let {
                    it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }
        context.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { r.onReceive(context, it) }
        onDispose { context.unregisterReceiver(r) }
    }

    val locale = Locale.getDefault()
    val usage = vm.usageMinutes?.let { m -> "Today's Usage: " + (if (m >= 60) "${m / 60}h " else "") + "${m % 60}m" } ?: "Grant usage access"
    val state = LauncherState(
        time = now.format(DateTimeFormatter.ofPattern(if (vm.use24h) "HH:mm" else "h:mm a", locale)),
        date = "${now.dayOfWeek.getDisplayName(TextStyle.FULL, locale)}, ${now.dayOfMonth}${ordinal(now.dayOfMonth)} ${now.month.getDisplayName(TextStyle.FULL, locale)}",
        battery = battery, charging = charging, usage = usage, usageGranted = vm.usageGranted,
        favourites = vm.favourites, dock = vm.dock, folders = vm.folders, results = vm.results,
        allApps = vm.allApps, hiddenKeys = vm.hiddenKeys, hiddenApps = vm.hiddenApps, query = vm.query,
        showDrawer = vm.showDrawer, showSettings = vm.showSettings,
        dnd = vm.dnd, dndGranted = vm.dndGranted, use24h = vm.use24h, swipe = vm.swipe, textSize = vm.textSize,
    )
    val actions = LauncherActions(
        showDrawer = { vm.showDrawer = it }, showSettings = { vm.showSettings = it }, query = vm::onQuery,
        launch = vm::launch, toggleFavourite = vm::toggleFavourite, toggleDock = vm::toggleDock,
        toggleHidden = vm::toggleHidden, uninstall = vm::uninstall, toggleFolder = vm::toggleFolder, newFolder = vm::newFolder,
        deleteFolder = vm::deleteFolder, toggleDnd = vm::toggleDnd, openUsageAccess = vm::openUsageAccess,
        openDndAccess = vm::openDndAccess, openDefaultLauncher = vm::openDefaultLauncher, set24h = vm::set24h, setSwipe = vm::chooseSwipe, setTextSize = vm::chooseTextSize,
    )
    LauncherContent(state, actions)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherContent(s: LauncherState, a: LauncherActions) {
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var folderPickerFor by remember { mutableStateOf<AppEntry?>(null) }
    var folderMenu by remember { mutableStateOf<String?>(null) }
    val expanded = remember { mutableStateListOf<String>() }

    BackHandler(enabled = s.showSettings) { a.showSettings(false) }
    BackHandler(enabled = s.showDrawer && !s.showSettings) { a.showDrawer(false) }

    Box(
        Modifier.fillMaxSize().background(Bg)
            .pointerInput(s.swipe, s.showDrawer, s.showSettings) {
                if (s.showSettings) return@pointerInput
                var dx = 0f; var dy = 0f; var fired = false
                detectDragGestures(onDragStart = { dx = 0f; dy = 0f; fired = false }) { _, drag ->
                    dx += drag.x; dy += drag.y
                    if (fired) return@detectDragGestures
                    val t = 80f
                    val open = when (s.swipe) {
                        SwipeDir.Up -> dy < -t && -dy > kotlin.math.abs(dx)
                        SwipeDir.Left -> dx < -t && -dx > kotlin.math.abs(dy)
                        SwipeDir.Right -> dx > t && dx > kotlin.math.abs(dy)
                    }
                    // For sideways swipes the opposite sideways swipe closes the list again.
                    val close = s.showDrawer && when (s.swipe) {
                        SwipeDir.Up -> false
                        SwipeDir.Left -> dx > t && dx > kotlin.math.abs(dy)
                        SwipeDir.Right -> dx < -t && -dx > kotlin.math.abs(dy)
                    }
                    if (!s.showDrawer && open) { fired = true; a.showDrawer(true) }
                    else if (close) { fired = true; a.showDrawer(false) }
                }
            }
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)
    ) {
        CompositionLocalProvider(LocalTextScale provides s.textSize.scale) { when {
            s.showSettings -> SettingsScreen(s, a)
            s.showDrawer -> Drawer(s, a, expanded, onAppMenu = { menuFor = it }, onFolderMenu = { folderMenu = it })
            else -> Home(s, a, expanded, onAppMenu = { menuFor = it }, onFolderMenu = { folderMenu = it })
        } }

        if (!s.showDrawer && !s.showSettings) when (s.swipe) {
            SwipeDir.Left -> Icon(Icons.Default.KeyboardArrowLeft, "Swipe left for all apps", tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.align(Alignment.CenterEnd))
            SwipeDir.Right -> Icon(Icons.Default.KeyboardArrowRight, "Swipe right for all apps", tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.align(Alignment.CenterStart))
            SwipeDir.Up -> Unit
        }
        menuFor?.let { app ->
            val isFav = s.favourites.any { it.key == app.key }
            val isDock = s.dock.any { it.key == app.key }
            val isHidden = app.key in s.hiddenKeys
            DropdownMenu(expanded = true, onDismissRequest = { menuFor = null }) {
                DropdownMenuItem(text = { Text(if (isFav) "Remove from home" else "Add to home") }, onClick = { a.toggleFavourite(app); menuFor = null })
                DropdownMenuItem(text = { Text(if (isDock) "Remove from dock" else "Add to dock") }, onClick = { a.toggleDock(app); menuFor = null })
                DropdownMenuItem(text = { Text("Folder…") }, onClick = { folderPickerFor = app; menuFor = null })
                DropdownMenuItem(text = { Text(if (isHidden) "Unhide" else "Hide") }, onClick = { a.toggleHidden(app); menuFor = null })
                DropdownMenuItem(text = { Text("Uninstall") }, onClick = { a.uninstall(app); menuFor = null })
            }
        }
        folderMenu?.let { name ->
            DropdownMenu(expanded = true, onDismissRequest = { folderMenu = null }) {
                DropdownMenuItem(text = { Text("Delete folder \"$name\"") }, onClick = { a.deleteFolder(name); folderMenu = null })
            }
        }
        folderPickerFor?.let { app ->
            var creating by remember { mutableStateOf(false) }
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { folderPickerFor = null },
                title = { Text(app.label) },
                text = {
                    Column {
                        if (creating) {
                            OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Folder name") })
                        } else {
                            s.folders.forEach { f ->
                                val member = f.apps.any { it.key == app.key }
                                Row(Modifier.fillMaxWidth().clickable { a.toggleFolder(app, f.name) }, verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(member, { a.toggleFolder(app, f.name) })
                                    Text(f.name)
                                }
                            }
                            TextButton({ creating = true }) { Text("New folder…") }
                        }
                    }
                },
                confirmButton = {
                    if (creating) TextButton({ a.newFolder(app, name); creating = false; name = "" }) { Text("Create") }
                    else TextButton({ folderPickerFor = null }) { Text("Done") }
                },
                dismissButton = { if (creating) TextButton({ creating = false }) { Text("Back") } },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Home(s: LauncherState, a: LauncherActions, expanded: SnapshotStateList<String>, onAppMenu: (AppEntry) -> Unit, onFolderMenu: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.time, color = Color.White, fontSize = sz(48))
            Text(s.date, color = Dim, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            BatteryIcon(s.battery, s.charging)
        }
        Column(Modifier.weight(1f).padding(top = 48.dp)) {
            s.favourites.forEach { app ->
                Text(
                    app.label, color = Color.White, fontSize = sz(24),
                    modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { a.launch(app) }, onLongClick = { onAppMenu(app) }).padding(vertical = 12.dp, horizontal = 8.dp)
                )
            }
            s.folders.forEach { f -> FolderBlock(f, f.name in expanded, sz(24), { toggle(expanded, f.name) }, a.launch, onAppMenu, onFolderMenu) }
            if (s.favourites.isEmpty() && s.folders.isEmpty()) Text("Swipe ${when (s.swipe) { SwipeDir.Up -> "up"; SwipeDir.Left -> "left"; SwipeDir.Right -> "right" }}, long-press an app, add to home", color = Color.Gray, modifier = Modifier.padding(8.dp))
        }
        if (s.swipe == SwipeDir.Up) Icon(Icons.Default.KeyboardArrowUp, "Swipe up for all apps", tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            s.usage, color = Dim, fontSize = 15.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().then(if (s.usageGranted) Modifier else Modifier.clickable { a.openUsageAccess() }).padding(bottom = 12.dp)
        )
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            s.dock.forEach { DockIcon(it, a.launch, onAppMenu) }
        }
    }
}

private fun toggle(list: SnapshotStateList<String>, name: String) { if (!list.remove(name)) list.add(name) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderHeader(name: String, open: Boolean, size: androidx.compose.ui.unit.TextUnit, onToggle: () -> Unit, onMenu: (String) -> Unit, color: Color = Color.White) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onToggle, onLongClick = { onMenu(name) }).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (open) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight, null, tint = color, modifier = Modifier.padding(horizontal = 8.dp))
        Text(name, color = color, fontSize = size)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderBlock(
    f: FolderUi, open: Boolean, size: androidx.compose.ui.unit.TextUnit, onToggle: () -> Unit,
    onLaunch: (AppEntry) -> Unit, onAppMenu: (AppEntry) -> Unit, onFolderMenu: (String) -> Unit,
) {
    FolderHeader(f.name, open, size, onToggle, onFolderMenu)
    if (open) {
        f.apps.forEach { app ->
            Text(
                app.label, color = Color.White, fontSize = size,
                modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onAppMenu(app) }).padding(start = 48.dp, top = 8.dp, bottom = 8.dp)
            )
        }
        if (f.apps.isEmpty()) Text("Empty. Long-press an app → Folder…", color = Color.Gray, modifier = Modifier.padding(start = 48.dp, bottom = 8.dp))
    }
}

private fun AppEntry.letter(): Char = label.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Drawer(s: LauncherState, a: LauncherActions, expanded: SnapshotStateList<String>, onAppMenu: (AppEntry) -> Unit, onFolderMenu: (String) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val searching = s.query.isNotEmpty()
    val letters = remember(s.results) { s.results.map { it.letter() }.distinct() }
    val headerCount = if (searching) 0 else s.folders.sumOf { 1 + if (it.name in expanded) maxOf(it.apps.size, 1) else 0 }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = s.query, onValueChange = a.query, singleLine = true, shape = RoundedCornerShape(50),
                placeholder = { Text("Search apps") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (s.query.isNotEmpty()) IconButton({ a.query("") }) { Icon(Icons.Default.Close, "Clear") } },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E1E1E), unfocusedContainerColor = Color(0xFF1E1E1E),
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { s.results.firstOrNull()?.let(a.launch) }),
            )
            IconButton(onClick = a.toggleDnd) { BellIcon(slashed = s.dnd) }
            IconButton(onClick = { a.showSettings(true) }) { Icon(Icons.Default.Settings, "Settings", tint = Dim, modifier = Modifier.size(22.dp)) }
        }
        Row(Modifier.weight(1f)) {
            LazyColumn(Modifier.weight(1f), state = listState) {
                if (!searching) s.folders.forEach { f ->
                    item(key = "f:${f.name}") { FolderHeader(f.name, f.name in expanded, sz(22), { toggle(expanded, f.name) }, onFolderMenu, Dim) }
                    if (f.name in expanded) {
                        items(f.apps, key = { "fa:${f.name}:${it.key}" }) { app -> AppRow(app, sz(20), 48.dp, a.launch, onAppMenu) }
                        if (f.apps.isEmpty()) item(key = "fe:${f.name}") { Text("Empty. Long-press an app → Folder…", color = Color.Gray, modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)) }
                    }
                }
                if (!searching && s.folders.isNotEmpty()) item(key = "divider") { Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f))) }
                items(s.results, key = { it.key }) { app -> AppRow(app, sz(22), 8.dp, a.launch, onAppMenu) }
            }
            if (!searching) AlphabetBar(letters, Modifier.align(Alignment.CenterVertically)) { letter ->
                val i = s.results.indexOfFirst { it.letter() == letter }
                if (i >= 0) scope.launch { listState.scrollToItem(headerCount + i) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(app: AppEntry, size: androidx.compose.ui.unit.TextUnit, start: androidx.compose.ui.unit.Dp, onLaunch: (AppEntry) -> Unit, onMenu: (AppEntry) -> Unit) {
    Text(
        app.label, color = Color.White, fontSize = size,
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onMenu(app) }).padding(start = start, top = 12.dp, bottom = 12.dp)
    )
}

@Composable
private fun SettingsScreen(s: LauncherState, a: LauncherActions) {
    var picking by remember { mutableStateOf(false) }
    BackHandler(enabled = picking) { picking = false }
    if (picking) { HidePicker(s, a) { picking = false }; return }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ a.showSettings(false) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text("Settings", color = Color.White, fontSize = 22.sp)
        }
        LazyColumn {
            item {
                SectionLabel("Display")
                SettingRow("24-hour clock", null, onClick = { a.set24h(!s.use24h) }) { Switch(s.use24h, { a.set24h(it) }) }
                ChoiceRow("Text size", TextSize.entries, s.textSize, { it.label }, a.setTextSize)
                SectionLabel("Gestures")
                ChoiceRow("Swipe to open all apps", SwipeDir.entries, s.swipe, { it.label }, a.setSwipe)
                SectionLabel("Permissions")
                SettingRow("Usage access", "Needed for \"Today's Usage\"", onClick = a.openUsageAccess) { StatusText(s.usageGranted) }
                SettingRow("Do Not Disturb access", "Needed for the Do Not Disturb (moon) button", onClick = a.openDndAccess) { StatusText(s.dndGranted) }
                SettingRow("Default launcher", "Open Android home settings", onClick = a.openDefaultLauncher)
                SectionLabel("Hidden apps")
                SettingRow("Hide more apps…", "Choose apps to remove from the app list", onClick = { picking = true })
                if (s.hiddenApps.isEmpty()) Text("None hidden", color = Color.Gray, modifier = Modifier.padding(8.dp))
            }
            items(s.hiddenApps, key = { it.key }) { app -> SettingRow(app.label, "Hidden from the app list", onClick = { a.toggleHidden(app) }) { Text("Unhide", color = Color.White, fontSize = 15.sp) } }
        }
    }
}

/** Full list of apps with a checkbox each; ticked apps are hidden from the drawer. */
@Composable
private fun HidePicker(s: LauncherState, a: LauncherActions, onBack: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    val shown = remember(s.allApps, filter) { s.allApps.filter { it.label.contains(filter.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text("Hide apps", color = Color.White, fontSize = 22.sp)
        }
        TextField(
            value = filter, onValueChange = { filter = it }, singleLine = true, shape = RoundedCornerShape(50),
            placeholder = { Text("Filter apps") }, leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E1E1E), unfocusedContainerColor = Color(0xFF1E1E1E),
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        LazyColumn {
            items(shown, key = { it.key }) { app ->
                Row(Modifier.fillMaxWidth().clickable { a.toggleHidden(app) }.padding(horizontal = 8.dp, vertical = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Checkbox(app.key in s.hiddenKeys, { a.toggleHidden(app) })
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = Dim, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 2.dp))
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Text(title, color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp, top = 14.dp))
    Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val on = o == selected
            Text(
                label(o), color = if (on) Color.Black else Color.White, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White else Color(0xFF1E1E1E))
                    .clickable { onSelect(o) }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun StatusText(on: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (on) "On" else "Off", color = if (on) Color.White else Dim, fontSize = 15.sp)
        Text("  ›", color = Dim, fontSize = 22.sp)
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String?, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 18.sp)
            if (subtitle != null) Text(subtitle, color = Dim, fontSize = 13.sp)
        }
        if (trailing != null) trailing() else Text("›", color = Dim, fontSize = 22.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockIcon(app: AppEntry, onLaunch: (AppEntry) -> Unit, onMenu: (AppEntry) -> Unit) {
    val mod = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF222222)).border(1.dp, Color(0xFF3A3A3A), CircleShape)
        .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onMenu(app) })
    val icon = app.icon
    Box(mod, contentAlignment = Alignment.Center) {
        if (icon != null) Image(icon, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.8f, colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }))
        else Text(app.label.take(1), color = Color.White, fontSize = 20.sp)
    }
}

@Composable
private fun BatteryIcon(level: Int, charging: Boolean) {
    Canvas(Modifier.size(width = 40.dp, height = 18.dp)) {
        val stroke = 2.dp.toPx(); val nub = 3.dp.toPx(); val r = 4.dp.toPx()
        val bodyW = size.width - nub
        drawRoundRect(Color.White, Offset(stroke / 2, stroke / 2), Size(bodyW - stroke, size.height - stroke), CornerRadius(r), style = Stroke(stroke))
        val pad = 3.dp.toPx()
        val fillW = (bodyW - 2 * pad) * level.coerceIn(0, 100) / 100f
        drawRoundRect(if (charging) Color(0xFF7CE38B) else Color.White, Offset(pad, pad), Size(fillW, size.height - 2 * pad), CornerRadius(r / 2))
        drawRoundRect(Color.White, Offset(bodyW, size.height * 0.3f), Size(nub, size.height * 0.4f), CornerRadius(1.dp.toPx()))
    }
}

@Composable
private fun BellIcon(slashed: Boolean) {
    // Crescent moon: outline when off, filled when Do Not Disturb is on.
    Canvas(Modifier.size(22.dp).semantics { contentDescription = "Do Not Disturb" }) {
        val moon = androidx.compose.ui.graphics.Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(Offset(size.width * 0.1f, size.height * 0.1f), Size(size.width * 0.8f, size.height * 0.8f)))
        }
        val cut = androidx.compose.ui.graphics.Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(Offset(size.width * 0.38f, -size.height * 0.02f), Size(size.width * 0.8f, size.height * 0.8f)))
        }
        val crescent = androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, moon, cut)
        drawPath(crescent, Dim, style = if (slashed) androidx.compose.ui.graphics.drawscope.Fill else Stroke(2.dp.toPx()))
    }
}

/**
 * Right-edge A–Z strip. While touched, letters bow toward the finger along a curve,
 * the letter under the finger grows, and a bubble with that letter follows it.
 */
@Composable
private fun AlphabetBar(letters: List<Char>, modifier: Modifier, onLetter: (Char) -> Unit) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var height by remember { mutableIntStateOf(1) }
    var last by remember { mutableStateOf<Char?>(null) }
    var touchY by remember { mutableStateOf<Float?>(null) }
    var anchorX by remember { mutableStateOf<Float?>(null) }   // x where the touch began; never follows the finger
    var widthPx by remember { mutableIntStateOf(1) }
    val itemPx = with(density) { 20.dp.toPx() }
    val clearance = with(density) { 44.dp.toPx() }   // how far left of the finger the arc peak sits
    val sigma = itemPx * 3.2f

    fun pick(y: Float) {
        if (letters.isEmpty()) return
        touchY = y.coerceIn(0f, height.toFloat())
        val l = letters[(y / height * letters.size).toInt().coerceIn(0, letters.size - 1)]
        if (l != last) { last = l; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onLetter(l) }
    }
    fun release() { touchY = null; anchorX = null; last = null }

    Box(modifier.width(32.dp)) {
        Column(
            Modifier.fillMaxWidth().testTag("alphabet").onSizeChanged { height = it.height.coerceAtLeast(1); widthPx = it.width.coerceAtLeast(1) }
                .pointerInput(letters) { detectDragGestures(onDragStart = { if (anchorX == null) anchorX = it.x; pick(it.y) }, onDragEnd = ::release, onDragCancel = ::release) { c, _ -> pick(c.position.y) } }
                .pointerInput(letters) { detectTapGestures(onPress = { anchorX = it.x; pick(it.y); if (tryAwaitRelease()) release() }) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEachIndexed { i, ch ->
                val d = touchY?.let { ((i + 0.5f) * itemPx - it) / sigma }
                val pull = if (d == null) 0f else kotlin.math.exp(-d * d)
                // Peak letter ends up `clearance` left of where the finger first landed.
                val peakShift = (anchorX ?: (widthPx / 2f)) - clearance - widthPx / 2f
                Box(
                    Modifier.height(20.dp).fillMaxWidth()
                        .offset { IntOffset((peakShift * pull).toInt(), 0) }
                        .graphicsLayer { val sc = 1f + 0.6f * pull; scaleX = sc; scaleY = sc },
                    contentAlignment = Alignment.Center,
                ) { Text(ch.toString(), color = Color.White.copy(alpha = 0.75f + 0.25f * pull), fontSize = 13.sp) }
            }
        }
        touchY?.let { y ->
            Box(
                Modifier.offset { IntOffset(((anchorX ?: 0f) - clearance - with(density) { 84.dp.toPx() }).toInt(), (y - with(density) { 28.dp.toPx() }).toInt()) }
                    .requiredSize(56.dp).clip(CircleShape).background(Color(0xFF2A2A2A)).border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text((last ?: ' ').toString(), color = Color.White, fontSize = 28.sp) }
        }
    }
}
