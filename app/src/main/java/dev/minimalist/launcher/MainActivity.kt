package dev.minimalist.launcher

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlin.math.roundToInt
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal val Bg = Color(0xFF0B0B0B)
private val LocalTextScale = compositionLocalOf { 1f }
private val LocalAddApps = staticCompositionLocalOf<(String) -> Unit> { {} }
@Composable private fun sz(v: Int) = (v * LocalTextScale.current).sp

internal val Dim = Color.White.copy(alpha = 0.7f)

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()
    private val homeRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refreshSystem() }

    /** Shows Android's own "set as default home app" dialog (10+), or the home settings page on older versions. */
    private fun requestDefaultHome() {
        vm.dismissHomePrompt()
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                homeRequest.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)); return
            }
        }
        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { LauncherScreen(vm, ::requestDefaultHome) } }
    }

    override fun onResume() { super.onResume(); vm.refresh(); vm.refreshSystem() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) { vm.showDrawer = false; vm.showSettings = false }
    }
}

private fun ordinal(d: Int) = if (d in 11..13) "th" else when (d % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }

@Composable
fun LauncherScreen(vm: LauncherViewModel, onRequestHome: () -> Unit) {
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
        favourites = vm.favourites, dock = vm.dock, folders = vm.folders, homeFolders = vm.homeFolders, results = vm.results,
        allApps = vm.allApps, hiddenKeys = vm.hiddenKeys, hiddenApps = vm.hiddenApps, query = vm.query,
        showDrawer = vm.showDrawer, showSettings = vm.showSettings,
        dnd = vm.dnd, dndGranted = vm.dndGranted, use24h = vm.use24h, swipe = vm.swipe, isDefaultHome = vm.isDefaultHome, showFolderCounts = vm.showFolderCounts, showHomePrompt = !vm.isDefaultHome && !vm.askedHomePrompt, textSize = vm.textSize, showFolderedApps = vm.showFolderedApps, showSwipeHint = vm.showSwipeHint,
    )
    val actions = LauncherActions(
        showDrawer = { vm.showDrawer = it }, showSettings = { vm.showSettings = it }, query = vm::onQuery,
        launch = vm::launch, toggleFavourite = vm::toggleFavourite, toggleDock = vm::toggleDock,
        toggleHidden = vm::toggleHidden, uninstall = vm::uninstall, toggleFolder = vm::toggleFolder, newFolder = vm::newFolder,
        deleteFolder = vm::deleteFolder, toggleDnd = vm::toggleDnd, openUsageAccess = vm::openUsageAccess,
        openDndAccess = vm::openDndAccess, openDefaultLauncher = onRequestHome, dismissHomePrompt = vm::dismissHomePrompt, setShowFolderCounts = vm::chooseShowFolderCounts, set24h = vm::set24h, setSwipe = vm::chooseSwipe, setTextSize = vm::chooseTextSize, moveFavourite = vm::moveFavourite, toggleHomeFolder = vm::toggleHomeFolder,
        moveHomeFolder = vm::moveHomeFolder, setShowFolderedApps = vm::chooseShowFoldered, setShowSwipeHint = vm::chooseShowHint,
    )
    LauncherContent(state, actions)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherContent(s: LauncherState, a: LauncherActions) {
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var folderMenu by remember { mutableStateOf<String?>(null) }
    var pickFolder by remember { mutableStateOf<String?>(null) }
    val expanded = remember { mutableStateListOf<String>() }

    BackHandler(enabled = s.showSettings) { a.showSettings(false) }
    BackHandler(enabled = s.showDrawer && !s.showSettings) { a.showDrawer(false) }

    Box(Modifier.fillMaxSize().background(Bg)) {
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    val scope = rememberCoroutineScope()
    // 0 = home fully shown, 1 = app list fully shown. While a finger is down both layers follow it;
    // on release they finish the move from wherever the finger left them.
    val anim = remember { Animatable(if (s.showDrawer) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragP by remember { mutableFloatStateOf(0f) }
    fun progress() = if (dragging) dragP else anim.value
    fun extent() = (if (s.swipe == SwipeDir.Up) size.height else size.width).toFloat().coerceAtLeast(1f)
    val homeVisible by remember { derivedStateOf { progress() < 1f } }
    val listVisible by remember { derivedStateOf { progress() > 0f } }

    LaunchedEffect(s.showDrawer) {
        val target = if (s.showDrawer) 1f else 0f
        if (!dragging && anim.targetValue != target) anim.animateTo(target, tween(300, easing = FastOutSlowInEasing))
    }

    /** Finishes a drag: [axisVelocity] is px/s, positive toward opening the list. */
    fun settle(axisVelocity: Float) {
        val target = when {
            axisVelocity > 900f -> 1f
            axisVelocity < -900f -> 0f
            else -> if (dragP > 0.5f) 1f else 0f
        }
        scope.launch {
            anim.snapTo(dragP); dragging = false
            a.showDrawer(target == 1f)
            anim.animateTo(target, spring(dampingRatio = 1f, stiffness = 380f), initialVelocity = axisVelocity / extent())
        }
    }

    Box(
        Modifier.fillMaxSize().onSizeChanged { size = it }
            .pointerInput(s.swipe, s.showDrawer, s.showSettings) {
                if (s.showSettings) return@pointerInput
                val vertical = s.swipe == SwipeDir.Up
                val sign = if (s.swipe == SwipeDir.Right) 1f else -1f   // finger direction that opens the list
                val tracker = VelocityTracker()
                var locked = false; var along = 0f; var across = 0f
                fun end() {
                    if (!locked) return
                    val v = tracker.calculateVelocity()
                    settle((if (vertical) v.y else v.x) * sign)
                    locked = false
                }
                detectDragGestures(
                    onDragStart = { locked = false; along = 0f; across = 0f; tracker.resetTracking() },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                ) { change, drag ->
                    val a1 = if (vertical) drag.y else drag.x
                    val c1 = if (vertical) drag.x else drag.y
                    tracker.addPosition(change.uptimeMillis, change.position)
                    along += a1; across += c1
                    if (!locked) {
                        val opening = !s.showDrawer && along * sign > 0
                        // For sideways swipes the opposite sideways drag pulls the list closed again.
                        val closing = s.showDrawer && !vertical && along * sign < 0
                        if ((opening || closing) && kotlin.math.abs(along) > kotlin.math.abs(across)) {
                            locked = true
                            dragP = (anim.value + along * sign / extent()).coerceIn(0f, 1f)
                            dragging = true
                            scope.launch { anim.stop() }
                        } else return@detectDragGestures
                    } else dragP = (dragP + a1 * sign / extent()).coerceIn(0f, 1f)
                    change.consume()
                }
            }
    ) {
        CompositionLocalProvider(LocalTextScale provides s.textSize.scale, LocalAddApps provides { pickFolder = it }) {
            if (homeVisible) Box(
                Modifier.fillMaxSize()
                    .offset {
                        val p = progress()
                        when (s.swipe) {
                            SwipeDir.Up -> IntOffset(0, (-p * size.height).roundToInt())
                            SwipeDir.Left -> IntOffset((-p * size.width).roundToInt(), 0)
                            SwipeDir.Right -> IntOffset((p * size.width).roundToInt(), 0)
                        }
                    }
                    .background(Bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)
            ) { Home(s, a, expanded, onAppMenu = { menuFor = it }, onFolderMenu = { folderMenu = it }) }

            if (listVisible) Box(
                Modifier.fillMaxSize()
                    .offset {
                        val q = 1f - progress()
                        when (s.swipe) {
                            SwipeDir.Up -> IntOffset(0, (q * size.height).roundToInt())
                            SwipeDir.Left -> IntOffset((q * size.width).roundToInt(), 0)
                            SwipeDir.Right -> IntOffset((-q * size.width).roundToInt(), 0)
                        }
                    }
                    .background(Bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)
            ) { Drawer(s, a, expanded, onAppMenu = { menuFor = it }, onFolderMenu = { folderMenu = it }) }

            AnimatedVisibility(s.showSettings, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
                Box(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) { SettingsScreen(s, a) }
            }
        }

        if (s.showSwipeHint && !s.showDrawer && !s.showSettings) when (s.swipe) {
            SwipeDir.Left -> Icon(Icons.Default.KeyboardArrowLeft, "Swipe left for all apps", tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.align(Alignment.CenterEnd))
            SwipeDir.Right -> Icon(Icons.Default.KeyboardArrowRight, "Swipe right for all apps", tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.align(Alignment.CenterStart))
            SwipeDir.Up -> Unit
        }
    }
    menuFor?.let { app -> AppSheet(app, s, a) { menuFor = null } }
    folderMenu?.let { name -> FolderSheet(name, s, a, onDismiss = { folderMenu = null }, onAddApps = { pickFolder = name; folderMenu = null }) }
    if (s.showHomePrompt && !s.showSettings) HomePromptSheet(onSet = a.openDefaultLauncher, onNotNow = a.dismissHomePrompt)
    pickFolder?.let { name ->
        BackHandler { pickFolder = null }
        Box(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            AppPickerScreen("Apps in $name", s.allApps, { app -> s.folders.firstOrNull { it.name == name }?.apps?.any { it.key == app.key } == true }, { a.toggleFolder(it, name) }) { pickFolder = null }
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Home(s: LauncherState, a: LauncherActions, expanded: SnapshotStateList<String>, onAppMenu: (AppEntry) -> Unit, onFolderMenu: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.time, color = Color.White, fontSize = sz(40))
            Text(s.date, color = Dim, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            BatteryIcon(s.battery, s.charging)
        }
        // Scrolls when favourites + folders overflow. Disabled for swipe-up, where a vertical
        // scroller would swallow the gesture that opens the app list.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState(), enabled = s.swipe != SwipeDir.Up).padding(top = 32.dp)) {
            ReorderColumn(s.favourites, { it.key }, { app, d -> a.moveFavourite(app, d) }, onAppMenu) { app, drag ->
                Text(
                    app.label, color = Color.White, fontSize = sz(20),
                    modifier = Modifier.fillMaxWidth().onTap(app.key) { a.launch(app) }.then(drag).padding(vertical = 9.dp, horizontal = 8.dp)
                )
            }
            ReorderColumn(s.homeFolders, { it.name }, { f, d -> a.moveHomeFolder(f.name, d) }, { onFolderMenu(it.name) }) { f, drag ->
                FolderBlock(f, f.name in expanded, sz(20), { toggle(expanded, f.name) }, a.launch, onAppMenu, onFolderMenu, drag)
            }
            if (s.favourites.isEmpty() && s.homeFolders.isEmpty()) Text("Swipe ${when (s.swipe) { SwipeDir.Up -> "up"; SwipeDir.Left -> "left"; SwipeDir.Right -> "right" }}, long-press an app, add to home", color = Color.Gray, modifier = Modifier.padding(8.dp))
        }
        if (s.showSwipeHint && s.swipe == SwipeDir.Up) Icon(Icons.Default.KeyboardArrowUp, "Swipe up for all apps", tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            s.usage, color = Dim, fontSize = 13.sp, textAlign = TextAlign.Center,
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
private fun FolderHeader(name: String, open: Boolean, size: androidx.compose.ui.unit.TextUnit, onToggle: () -> Unit, onMenu: (String) -> Unit, color: Color = Color.White, drag: Modifier? = null, info: String? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (drag != null) Modifier.onTap(name, onToggle).then(drag) else Modifier.combinedClickable(onClick = onToggle, onLongClick = { onMenu(name) })).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (open) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight, null, tint = color, modifier = Modifier.padding(horizontal = 8.dp))
        Text(name, color = color, fontSize = size, modifier = Modifier.weight(1f))
        if (info != null) Text(info, color = Dim, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderBlock(
    f: FolderUi, open: Boolean, size: androidx.compose.ui.unit.TextUnit, onToggle: () -> Unit,
    onLaunch: (AppEntry) -> Unit, onAppMenu: (AppEntry) -> Unit, onFolderMenu: (String) -> Unit, drag: Modifier? = null,
) {
    FolderHeader(f.name, open, size, onToggle, onFolderMenu, drag = drag)
    if (open) {
        f.apps.forEach { app ->
            Text(
                app.label, color = Color.White, fontSize = size,
                modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onAppMenu(app) }).padding(start = 48.dp, top = 8.dp, bottom = 8.dp)
            )
        }
        if (f.apps.isEmpty()) EmptyFolderHint(f.name)
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
                    item(key = "f:${f.name}") { FolderHeader(f.name, f.name in expanded, sz(18), { toggle(expanded, f.name) }, onFolderMenu, Dim, info = listOfNotNull(if (s.showFolderCounts) "${f.apps.size}" else null, if (s.homeFolders.any { it.name == f.name }) "on home" else null).joinToString(" · ").ifEmpty { null }) }
                    if (f.name in expanded) {
                        items(f.apps, key = { "fa:${f.name}:${it.key}" }) { app -> AppRow(app, sz(17), 48.dp, a.launch, onAppMenu) }
                        if (f.apps.isEmpty()) item(key = "fe:${f.name}") { EmptyFolderHint(f.name) }
                    }
                }
                items(s.results, key = { it.key }) { app -> AppRow(app, sz(18), 8.dp, a.launch, onAppMenu) }
            }
            if (!searching) AlphabetBar(
                (if (s.folders.isNotEmpty()) listOf(FOLDERS) else emptyList()) + letters,
                Modifier.align(Alignment.CenterVertically),
            ) { letter ->
                if (letter == FOLDERS) { scope.launch { listState.scrollToItem(0) } }
                else {
                    val i = s.results.indexOfFirst { it.letter() == letter }
                    if (i >= 0) scope.launch { listState.scrollToItem(headerCount + i) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(app: AppEntry, size: androidx.compose.ui.unit.TextUnit, start: androidx.compose.ui.unit.Dp, onLaunch: (AppEntry) -> Unit, onMenu: (AppEntry) -> Unit) {
    Text(
        app.label, color = Color.White, fontSize = size,
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onMenu(app) }).padding(start = start, top = 9.dp, bottom = 9.dp)
    )
}

@Composable
private fun SettingsScreen(s: LauncherState, a: LauncherActions) {
    var picking by remember { mutableStateOf(false) }
    BackHandler(enabled = picking) { picking = false }
    if (picking) { AppPickerScreen("Hide apps", s.allApps, { it.key in s.hiddenKeys }, a.toggleHidden) { picking = false }; return }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ a.showSettings(false) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text("Settings", color = Color.White, fontSize = 22.sp)
        }
        LazyColumn {
            item {
                SectionLabel("Display")
                SettingRow("24-hour clock", null, onClick = { a.set24h(!s.use24h) }) { Switch(s.use24h, { a.set24h(it) }, colors = monoSwitchColors()) }
                CycleRow("Text size", TextSize.entries, s.textSize, { it.label }, a.setTextSize)
                SettingRow("Show app count on folders", null, onClick = { a.setShowFolderCounts(!s.showFolderCounts) }) { Switch(s.showFolderCounts, { a.setShowFolderCounts(it) }, colors = monoSwitchColors()) }
                SettingRow("Apps in folders stay in the list", "Off: an app in a folder is only listed in its folder (search still finds it)", onClick = { a.setShowFolderedApps(!s.showFolderedApps) }) { Switch(s.showFolderedApps, { a.setShowFolderedApps(it) }, colors = monoSwitchColors()) }
                SectionLabel("Gestures")
                CycleRow("Swipe to open all apps", SwipeDir.entries, s.swipe, { it.label }, a.setSwipe)
                SettingRow("Show swipe hint", "A faint arrow showing where to swipe", onClick = { a.setShowSwipeHint(!s.showSwipeHint) }) { Switch(s.showSwipeHint, { a.setShowSwipeHint(it) }, colors = monoSwitchColors()) }
                SectionLabel("Permissions")
                SettingRow("Usage access", "Needed for \"Today's Usage\"", onClick = a.openUsageAccess) { StatusText(s.usageGranted) }
                SettingRow("Do Not Disturb access", "Needed for the Do Not Disturb (moon) button", onClick = a.openDndAccess) { StatusText(s.dndGranted) }
                SettingRow("Home app", if (s.isDefaultHome) "This launcher opens when you press Home" else "Not your home app. Tap to set it", onClick = a.openDefaultLauncher) {
                    Text(if (s.isDefaultHome) "Yes" else "No", color = if (s.isDefaultHome) Color.White else Dim, fontSize = 15.sp)
                }
                SectionLabel("Hidden apps")
                SettingRow("Hide more apps…", "Choose apps to remove from the app list", onClick = { picking = true })
                if (s.hiddenApps.isEmpty()) Text("None hidden", color = Color.Gray, modifier = Modifier.padding(8.dp))
            }
            items(s.hiddenApps, key = { it.key }) { app -> SettingRow(app.label, "Hidden from the app list", onClick = { a.toggleHidden(app) }) { Text("Unhide", color = Color.White, fontSize = 15.sp) } }
        }
    }
}

/** Full-screen list of every app with a checkbox each, and a filter. Used for hiding apps and filling folders. */
@Composable
internal fun AppPickerScreen(title: String, apps: List<AppEntry>, isChecked: (AppEntry) -> Boolean, onToggle: (AppEntry) -> Unit, onBack: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    val shown = remember(apps, filter) { apps.filter { it.label.contains(filter.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text(title, color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
            TextButton(onBack, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Done") }
        }
        Text("Tap an app to add or remove it. Changes save immediately.", color = Dim, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
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
                Row(Modifier.fillMaxWidth().clickable { onToggle(app) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Checkbox(isChecked(app), { onToggle(app) }, colors = monoCheckboxColors())
                }
            }
        }
    }
}

@Composable
private fun EmptyFolderHint(folder: String) {
    val addApps = LocalAddApps.current
    Column(Modifier.padding(start = 48.dp, bottom = 4.dp)) {
        Text("No apps yet.", color = Color.Gray, fontSize = 14.sp)
        Text("+ Add apps", color = Color.White, fontSize = 15.sp, modifier = Modifier.clickable { addApps(folder) }.padding(vertical = 8.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = Dim, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 2.dp))
}

/** A row that shows the current choice on the right; tapping it moves to the next option. */
@Composable
private fun <T> CycleRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SettingRow(title, null, onClick = { onSelect(options[(options.indexOf(selected) + 1) % options.size]) }) {
        Text(label(selected), color = Dim, fontSize = 15.sp)
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
    val mod = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF222222)).border(1.dp, Color(0xFF3A3A3A), CircleShape)
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

/** Marks the folder shortcut at the top of the side strip; jumps to the folders at the top of the list. */
private const val FOLDERS = '\u0001'

@Composable
private fun FolderGlyph(size: androidx.compose.ui.unit.Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        drawRoundRect(color, Offset(w * 0.06f, h * 0.30f), Size(w * 0.88f, h * 0.56f), CornerRadius(w * 0.1f), style = Stroke(w * 0.11f))
        drawRoundRect(color, Offset(w * 0.06f, h * 0.16f), Size(w * 0.40f, h * 0.16f), CornerRadius(w * 0.05f))
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
                ) { if (ch == FOLDERS) FolderGlyph(13.dp, Color.White.copy(alpha = 0.75f + 0.25f * pull)) else Text(ch.toString(), color = Color.White.copy(alpha = 0.75f + 0.25f * pull), fontSize = 13.sp) }
            }
        }
        touchY?.let { y ->
            Box(
                Modifier.offset { IntOffset(((anchorX ?: 0f) - clearance - with(density) { 84.dp.toPx() }).toInt(), (y - with(density) { 28.dp.toPx() }).toInt()) }
                    .requiredSize(56.dp).clip(CircleShape).background(Color(0xFF2A2A2A)).border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (last == FOLDERS) FolderGlyph(28.dp, Color.White) else Text((last ?: ' ').toString(), color = Color.White, fontSize = 28.sp) }
        }
    }
}
