<div align="center">

<img src="assets/icon.svg" width="96" height="96" alt="Minimalist Launcher icon">

# Minimalist Launcher

A calm Android home screen: a clock, a short list of the apps you actually use, and everything else one swipe away.

[Features](#features) · [Install](#install) · [Gestures](#gestures) · [Build](#build) · [ADB control](#control-it-over-adb)

</div>

Minimalist Launcher replaces the stock home screen with plain text, a near-black background and no icons except a small dock. It is built to make you more conscious of your phone, not to grab your attention. It has no internet permission, no ads and no analytics.

> [!NOTE]
> The app is called **Minimal** on the device. Android 8.0 (API 26) or newer is required.

## Features

- **Home screen:** clock, date, battery, your favourite apps as a text list, folders you choose to pin, today's screen time, and a dock of up to five apps.
- **App list:** a search bar, every app in alphabetical order, folders at the top, and a side strip (A–Z, plus a folder shortcut) you can slide along to jump.
- **Folders:** an app can be in several folders. Only one folder is open at a time. Apps in a folder leave the main list by default (a setting brings them back).
- **Drag to reorder:** long-press a home row and drag it to a new place.
- **Rename and hide:** give any app your own name (search still matches the original), or hide it from the list.
- **Gestures:** assign actions to swipes, double tap and long press. See [Gestures](#gestures).
- **Text size and 12/24-hour clock**, plus a first-launch prompt to become your default home app.

Long-press any app or folder for its actions: add to or remove from home, dock and folders, rename, hide and uninstall.

## Install

[Build the APK](#build), copy it to your phone and open it. Android asks you to allow installs from the app you opened it with.

After installing, press Home and choose **Minimal** as your home app, or accept the prompt on first launch.

### Permissions

Everything below is optional. The launcher works without any of it.

| Permission | Used for |
| --- | --- |
| Usage access | The "Today's Usage" line on the home screen |
| Do Not Disturb access | The moon button in the app list |
| Accessibility service ("Minimal gestures") | Locking the screen with a gesture, and a backup for opening notifications |

The accessibility service reads nothing from the screen. It only asks Android to lock the screen or open the notification shade. Each permission is switched on from Settings → Permissions.

## Gestures

Settings → Gestures lets you choose an action for each gesture on the home screen. One swipe direction always opens the app list; you pick which. The others are free.

| Gesture | Default |
| --- | --- |
| Swipe down | Open notifications |
| Long press on empty space | Open launcher settings |
| Double tap, and the two free swipe directions | Do nothing |

Available actions: open the notifications or quick settings, search apps, lock the screen, toggle the flashlight, toggle Do Not Disturb, open launcher settings, or open an app of your choice.

> [!TIP]
> Dragging the app list open follows your finger and finishes from where you let go. A quick flick opens it from a short distance.

## Build

Requirements: JDK 17 and the Android SDK with platform 35.

```sh
git clone https://github.com/D4NZ-jpg/minimalist-launcher.git
cd minimalist-launcher
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew assembleDebug      # app/build/outputs/apk/debug/
./gradlew assembleRelease    # app/build/outputs/apk/release/
```

> [!NOTE]
> The release build is optimised with R8 and signed with the debug key, so it can be installed over a debug build. It is meant for personal sideloading, not for publishing.

### Tests

```sh
./gradlew testDebugUnitTest      # unit and UI tests on the JVM
./gradlew recordRoborazziDebug   # same tests, and writes screenshots to app/build/screenshots/
```

The UI tests render screens with fake apps using Robolectric and Roborazzi, so no emulator is needed.

## Control it over ADB

A hidden receiver lets a script or coding agent read and rearrange your layout. It has no UI, and it requires the `DUMP` permission, which only the `adb` shell holds, so other apps cannot call it.

Read the current layout (apps, favourites, dock, hidden apps, folders, folders on home and custom names):

```sh
adb shell am broadcast -n dev.minimalist.launcher/.AgentReceiver \
  -a dev.minimalist.launcher.AGENT --es cmd list
```

Replace parts of it. Sections you leave out are unchanged, and unknown app keys are dropped and reported:

```sh
adb shell am broadcast -n dev.minimalist.launcher/.AgentReceiver \
  -a dev.minimalist.launcher.AGENT --es cmd apply \
  --es plan "$(base64 < plan.json | tr -d '\n')"
```

```json
{
  "favourites": ["com.example.chat/com.example.chat.Main"],
  "dock": ["com.example.dialer/com.example.dialer.Main"],
  "folders": { "Work": ["com.example.mail/com.example.mail.Main"] },
  "homeFolders": ["Work"],
  "hidden": [],
  "aliases": { "com.example.chat/com.example.chat.Main": "Messages" }
}
```

App keys are `package/activity`, exactly as `list` returns them. The reply is in the `data="..."` part of the broadcast result, and the launcher refreshes on its own.

## Project layout

| File | Role |
| --- | --- |
| `MainActivity.kt` | Screens: home, app list, settings, and the drag that opens the list |
| `Sheets.kt`, `Reorder.kt` | Bottom sheets and drag-to-reorder |
| `LauncherViewModel.kt`, `LauncherUi.kt` | State and the actions the UI can call |
| `AppRepository.kt` | Installed apps and everything saved: favourites, dock, folders, names, settings |
| `Gestures.kt`, `SystemActions.kt`, `LockAccessibilityService.kt` | Gesture bindings and what they do |
| `AgentReceiver.kt`, `AgentApi.kt` | The ADB control channel |

Built with Kotlin and Jetpack Compose.

Created in [T3 Code](https://t3.codes).
