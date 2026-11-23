package dev.minimalist.launcher

/** Everything the user can do on the home screen with a finger, other than tapping an app. */
enum class Gesture(val label: String) {
    SwipeUp("Swipe up"), SwipeDown("Swipe down"), SwipeLeft("Swipe left"), SwipeRight("Swipe right"),
    DoubleTap("Double tap"), LongPress("Long press on empty space"),
}

/** What a gesture can do. [AppList] is only ever bound to the swipe that opens the app list. */
enum class GestureAction(val label: String) {
    None("Do nothing"),
    AppList("Open all apps"),
    Notifications("Open notifications"),
    QuickSettings("Open quick settings"),
    Search("Search apps"),
    LockScreen("Lock the screen"),
    Flashlight("Toggle flashlight"),
    Dnd("Toggle Do Not Disturb"),
    LauncherSettings("Open launcher settings"),
    LaunchApp("Open an app"),
}

/** An action, plus the app to open when the action is [GestureAction.LaunchApp]. */
data class GestureBinding(val action: GestureAction, val appKey: String? = null)

/** The finger movement that opens the app list. */
fun SwipeDir.gesture(): Gesture = when (this) {
    SwipeDir.Up -> Gesture.SwipeUp
    SwipeDir.Left -> Gesture.SwipeLeft
    SwipeDir.Right -> Gesture.SwipeRight
}

/** Actions a user may choose for a gesture (the app-list action is fixed to its own swipe). */
val ChoosableActions = GestureAction.entries.filter { it != GestureAction.AppList }
