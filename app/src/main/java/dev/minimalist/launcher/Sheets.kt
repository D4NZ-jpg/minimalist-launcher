package dev.minimalist.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SheetBg = Color(0xFF161616)
private val Chip = Color(0xFF262626)

/** Bottom action sheet with a scrim. Tapping outside or pressing Back closes it. */
@Composable
private fun SheetHost(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            )
        }
        AnimatedVisibility(
            visible, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
            exit = slideOutVertically(tween(180)) { it },
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(SheetBg)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .navigationBarsPadding().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SheetLabel(text: String) {
    Text(text.uppercase(), color = Dim, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

private val Danger = Color(0xFFE5484D)

/** A plain tappable row. The label itself says what will happen, e.g. "Add to home" / "Remove from home". */
@Composable
private fun ActionRow(label: String, subtitle: String? = null, enabled: Boolean = true, color: Color = Color.White, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 11.dp)) {
        Text(label, color = if (enabled) color else Dim, fontSize = 16.sp)
        if (subtitle != null) Text(subtitle, color = Dim, fontSize = 13.sp)
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, subtitle: String? = null, enabled: Boolean = true, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onToggle).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (enabled) Color.White else Dim, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = Dim, fontSize = 13.sp)
        }
        Switch(on, { onToggle() }, enabled = enabled, colors = monoSwitchColors())
    }
}

@Composable
fun AppSheet(app: AppEntry, s: LauncherState, a: LauncherActions, onRename: () -> Unit, onDismiss: () -> Unit) {
    val favIndex = s.favourites.indexOfFirst { it.key == app.key }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val inFolder = s.folders.any { f -> f.apps.any { it.key == app.key } }
    // While an app sits in a folder it is already out of the main list, so hiding adds nothing (unless it is already hidden).
    val redundantHide = inFolder && !s.showFolderedApps && app.key !in s.hiddenKeys
    SheetHost(onDismiss) {
        SheetTitle(app.label)
        ActionRow(if (favIndex >= 0) "Remove from home" else "Add to home") { a.toggleFavourite(app) }
        ActionRow(if (s.dock.any { it.key == app.key }) "Remove from dock" else "Add to dock") { a.toggleDock(app) }
        ActionRow(
            if (app.key in s.hiddenKeys) "Show in main app list" else "Hide from main app list",
            subtitle = if (redundantHide) "Shown inside its folders only" else null,
            enabled = !redundantHide,
        ) { a.toggleHidden(app) }
        ActionRow("Rename app", onClick = onRename)
        SheetLabel("Add to folders")
        s.folders.forEach { f ->
            val member = f.apps.any { it.key == app.key }
            Row(Modifier.fillMaxWidth().clickable { a.toggleFolder(app, f.name) }, verticalAlignment = Alignment.CenterVertically) {
                Text(f.name, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Checkbox(member, { a.toggleFolder(app, f.name) }, colors = monoCheckboxColors())
            }
        }
        if (s.folders.isEmpty()) Text("No folders yet.", color = Dim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
        if (s.folders.any { f -> f.apps.any { it.key == app.key } } && !s.showFolderedApps)
            Text("Apps in a folder are listed only inside their folders.", color = Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        if (!creating) Text("+ New folder", color = Color.White, fontSize = 16.sp, modifier = Modifier.clickable { creating = true }.padding(vertical = 12.dp))
        if (creating) {
            TextField(
                value = name, onValueChange = { name = it }, singleLine = true, shape = RoundedCornerShape(50),
                placeholder = { Text("Folder name") },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Chip, unfocusedContainerColor = Chip,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { a.newFolder(app, name); creating = false; name = "" }),
                supportingText = { Text("${app.label} will be added to this folder.", color = Dim) },
                trailingIcon = { TextButton({ a.newFolder(app, name); creating = false; name = "" }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Create") } },
            )
        }
        Spacer(Modifier.height(8.dp))
        ActionRow("Uninstall", color = Danger) { a.uninstall(app); onDismiss() }
    }
}

@Composable
fun FolderSheet(folder: String, s: LauncherState, a: LauncherActions, onDismiss: () -> Unit, onAddApps: () -> Unit) {
    val i = s.homeFolders.indexOfFirst { it.name == folder }
    SheetHost(onDismiss) {
        SheetTitle(folder)
        ActionRow(if (i >= 0) "Remove from home screen" else "Add to home screen") { a.toggleHomeFolder(folder) }
        Text(
            if (i >= 0) "On the home screen. Long-press and drag it there to reorder." else "The folder always stays in the app list. Add it to the home screen to pin it there too.",
            color = Dim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp),
        )
        ActionRow("Add apps…", onClick = onAddApps)
        ActionRow("Delete folder (apps stay installed)", color = Danger) { a.deleteFolder(folder); onDismiss() }
    }
}

/** Small sheet for giving an app your own name. Only appears when you ask to rename. */
@Composable
fun RenameSheet(app: AppEntry, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(app.label) }
    SheetHost(onDismiss) {
        SheetTitle("Rename app")
        TextField(
            value = name, onValueChange = { name = it }, singleLine = true, shape = RoundedCornerShape(50),
            placeholder = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Chip, unfocusedContainerColor = Chip,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(name) }),
            supportingText = { Text("Original name: ${app.original}", color = Dim) },
        )
        Text(
            "Save", color = if (name.isBlank()) Dim else Color.Black, fontSize = 15.sp,
            modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(50))
                .background(if (name.isBlank()) Chip else Color.White)
                .clickable(enabled = name.isNotBlank()) { onSave(name) }.padding(horizontal = 18.dp, vertical = 10.dp),
        )
        if (app.label != app.original) ActionRow("Use the original name") { onSave(null) }
    }
}

/** Lists what a gesture can do. Picking "Open an app" is followed by an app picker. */
@Composable
fun GestureSheet(gesture: Gesture, current: GestureBinding, accessibilityOn: Boolean, onPick: (GestureAction) -> Unit, onDismiss: () -> Unit) {
    SheetHost(onDismiss) {
        SheetTitle(gesture.label)
        ChoosableActions.forEach { act ->
            Row(Modifier.fillMaxWidth().clickable { onPick(act) }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(act.label, color = Color.White, fontSize = 16.sp)
                    if (act == GestureAction.LockScreen && !accessibilityOn) Text("Needs the Accessibility service (Settings)", color = Dim, fontSize = 13.sp)
                }
                if (act == current.action) Text("✓", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}

/** One-time first-launch offer to become the default home app. */
@Composable
fun HomePromptSheet(onSet: () -> Unit, onNotNow: () -> Unit) {
    SheetHost(onNotNow) {
        SheetTitle("Make this your home app")
        Text(
            "Pressing Home will open this launcher. Android will ask you to confirm.",
            color = Dim, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp),
        )
        Text(
            "Set as home app", color = Color.Black, fontSize = 15.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onSet).padding(horizontal = 18.dp, vertical = 10.dp),
        )
        Text("Not now", color = Dim, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp).clickable(onClick = onNotNow).padding(vertical = 10.dp))
    }
}

/** Black-and-white switch: no accent colour. */
@Composable
internal fun monoSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.Black, checkedTrackColor = Color.White, checkedBorderColor = Color.White,
    uncheckedThumbColor = Color(0xFF9A9A9A), uncheckedTrackColor = Color(0xFF1E1E1E), uncheckedBorderColor = Color(0xFF555555),
)

/** Black-and-white checkbox. */
@Composable
internal fun monoCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = Color.White, uncheckedColor = Color(0xFF8A8A8A), checkmarkColor = Color.Black,
)
