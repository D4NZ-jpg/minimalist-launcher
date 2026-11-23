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
fun AppSheet(app: AppEntry, s: LauncherState, a: LauncherActions, onDismiss: () -> Unit) {
    val favIndex = s.favourites.indexOfFirst { it.key == app.key }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val inFolder = s.folders.any { f -> f.apps.any { it.key == app.key } }
    // While an app sits in a folder it is already out of the main list, so hiding adds nothing (unless it is already hidden).
    val redundantHide = inFolder && !s.showFolderedApps && app.key !in s.hiddenKeys
    SheetHost(onDismiss) {
        SheetTitle(app.label)
        ToggleRow("Show on home", favIndex >= 0) { a.toggleFavourite(app) }
        ToggleRow("Show in dock", s.dock.any { it.key == app.key }) { a.toggleDock(app) }
        ToggleRow(
            "Hide from main app list", app.key in s.hiddenKeys,
            subtitle = if (redundantHide) "Shown inside its folders only" else null,
            enabled = !redundantHide,
        ) { a.toggleHidden(app) }
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
        Text(
            "Uninstall", color = Dim, fontSize = 16.sp,
            modifier = Modifier.padding(top = 16.dp).clickable { a.uninstall(app); onDismiss() }.padding(vertical = 8.dp),
        )
    }
}

@Composable
fun FolderSheet(folder: String, s: LauncherState, a: LauncherActions, onDismiss: () -> Unit, onAddApps: () -> Unit) {
    val i = s.homeFolders.indexOfFirst { it.name == folder }
    SheetHost(onDismiss) {
        SheetTitle(folder)
        ToggleRow("Show on home screen", i >= 0) { a.toggleHomeFolder(folder) }
        Text(
            if (i >= 0) "Shown on the home screen. Long-press and drag it there to reorder." else "The folder always stays in the app list. Turn this on to also pin it to the home screen.",
            color = Dim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp),
        )
        Text("Add apps…", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp).clickable(onClick = onAddApps).padding(vertical = 8.dp))
        Text(
            "Delete folder (apps stay installed)", color = Dim, fontSize = 16.sp,
            modifier = Modifier.padding(top = 12.dp).clickable { a.deleteFolder(folder); onDismiss() }.padding(vertical = 8.dp),
        )
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

/** Names a new folder; the caller opens the app picker for it afterwards. */
@Composable
fun NewFolderSheet(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    SheetHost(onDismiss) {
        SheetTitle("New folder")
        TextField(
            value = name, onValueChange = { name = it }, singleLine = true, shape = RoundedCornerShape(50),
            placeholder = { Text("Folder name") }, modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Chip, unfocusedContainerColor = Chip,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onCreate(name) }),
            supportingText = { Text("You pick its apps next.", color = Dim) },
        )
        Text(
            "Create and choose apps", color = if (name.isBlank()) Dim else Color.Black, fontSize = 15.sp,
            modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(50))
                .background(if (name.isBlank()) Chip else Color.White)
                .clickable(enabled = name.isNotBlank()) { onCreate(name) }.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}
