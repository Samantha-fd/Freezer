# Freezer

Freezer is an Android app for temporarily pausing selected apps without uninstalling them. It uses [Shizuku](https://shizuku.rikka.app/) to request Android package operations without root.

[Download the latest release](https://github.com/Samantha-fd/Freezer/releases/latest) · [Report an issue](https://github.com/Samantha-fd/Freezer/issues)

## What freezing means

Freezer first attempts to **suspend** an app using Android's package manager. While suspended, the app's activities cannot be opened and its notifications are hidden. Its data remains on the device, and its launcher icon generally stays visible; the launcher controls how that icon appears.

Suspension does **not** guarantee that all background activity stops. Battery savings have not been measured. Freezer does not currently report battery savings.

If suspension is unavailable, Freezer may offer to **disable** the app instead, with your confirmation. A disabled app's launcher icon may disappear or move, and its original position may not be restored when it is re-enabled. Some system-protected apps cannot be suspended or disabled.

## Features

- Search installed apps by name or package name and select multiple apps to freeze.
- Manage up to 16 frozen apps in a grid; tap an app and confirm to unfreeze it.
- Optionally protect Freezer with the device screen lock. On supported devices running Android 11 or later, strong biometrics can also be used.
- Add a home-screen widget to unfreeze a selected app through Freezer.
- Restore saved frozen-app entries and check their state when Shizuku is available.
- Display Shizuku's status and open the Shizuku app from the status banner when it needs to be started.

## Requirements

- Android 8.0 (API 26) or later.
- [Shizuku](https://shizuku.rikka.app/) installed, running, and authorised for Freezer. Wireless debugging requires Android 11 or later; see the [Shizuku setup guide](https://shizuku.rikka.app/guide/setup/) for other startup methods.

Freezer asks Android for broad installed-app visibility so it can display an app picker. It filters out known sensitive packages and detected launchers, but these safeguards do not cover every device or every important app. Choose apps carefully.

## Install and use

1. Download `freezer-v2.0.0-release.apk` from the [latest release](https://github.com/Samantha-fd/Freezer/releases/latest) and install it. Allow installation from your chosen source if Android asks.
2. Install and start Shizuku, following its [setup guide](https://shizuku.rikka.app/guide/setup/).
3. Open Freezer and tap **+**. If prompted, grant Shizuku access, then tap **+** again.
4. Search or browse for apps, select the ones you want, and tap **Freeze selected**.
5. To unfreeze an app, open Freezer, tap the app, and confirm **Unfreeze**.

For non-root Shizuku started through wireless debugging, start the Shizuku service again after a device reboot. Pairing is normally a separate, one-time setup step. Freezer can still show saved entries while Shizuku is unavailable, but it needs Shizuku running and authorised to change their state.

**Before uninstalling Freezer or clearing its data, unfreeze your apps.** Freezer stores its records locally; removing them can make apps harder to identify and restore. If you install an update over an existing installation, Android requires a compatible signing certificate and application ID. A release-signed APK cannot normally update a debug-signed installation or vice versa.

## Home-screen widget

After freezing an app, add the Freezer widget from your launcher's widget picker and select the app. Tapping the widget opens Freezer to unfreeze it. If app lock is enabled, unlock Freezer first. A widget becomes unavailable when its selected app is no longer in Freezer's frozen list.

## Limitations

- Suspension can leave background activity running. Freezer makes no measured claim about battery savings.
- The appearance of suspended apps and placement of re-enabled apps vary by launcher and Android version.
- Freezer supports up to 16 frozen apps at a time. Some apps or device configurations cannot be managed through Shizuku.
- The suspension message is shown by Android; its title and appearance can vary by device.
- Freezer is distributed through GitHub Releases. It requests broad package visibility, which is subject to [Google Play policy](https://support.google.com/googleplay/android-developer/answer/10158779) if Play distribution is considered.

## Build from source

The project uses Kotlin, Jetpack Compose, Shizuku, and SharedPreferences with Kotlin serialization. It uses Android Gradle Plugin 9.0.1 and the Gradle wrapper.

Requirements:

- Android Studio Otter 3 Feature Drop (2025.2.3) or a newer version compatible with AGP 9.0.
- Android SDK Platform 36 and a JDK compatible with AGP 9.0 (JDK 17 or later).

Clone the repository and open it in Android Studio, or build from a terminal at the project root:

```bash
git clone https://github.com/Samantha-fd/Freezer.git
cd Freezer
./gradlew assembleDebug testDebugUnitTest
```

On Windows, use `gradlew.bat assembleDebug testDebugUnitTest`. The debug APK is generated under `app/build/outputs/apk/debug/`. Debug builds use a debug signing key and are intended for testing.

Release APKs must be signed with the release key. Keep the keystore and passwords private and backed up; do not commit them. Use names such as `freezer-v2.0.0-release.apk` for a signed release and `freezer-v2.0.0-debug.apk` for a test build.

## Contributing and support

Bug reports and suggestions are welcome through [GitHub Issues](https://github.com/Samantha-fd/Freezer/issues). For a bug report, include the Freezer version, Android version, device model, steps to reproduce, and what you expected to happen. Please avoid including passwords, signing keys, or personal information.

## Licence

The project's source code is available under the [MIT licence](LICENSE).
