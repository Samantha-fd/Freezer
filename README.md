# Freezer

An Android app that pauses selected apps without uninstalling them. It uses [Shizuku](https://shizuku.rikka.app/) for privileged access — no root required.

By default, Freezer suspends selected apps using Android's package manager. While suspended:

- Their activities cannot be opened, and notifications are hidden.
- Their icons generally remain visible, with an appearance controlled by the launcher.
- Their data remains on the device and the apps can be unsuspended.
- Background activity may continue, and battery savings have not been measured.

If suspension is unavailable, Freezer can offer to disable the app after asking for confirmation. Disabling may remove or move its launcher icon.

## Why

Freezer provides a simple way to temporarily make distracting apps unavailable without uninstalling them. It uses Shizuku to request Android package suspension and, with confirmation when needed, disabling. These methods have different effects on launcher icons and background activity.

## Tech stack

- **Kotlin** + **Jetpack Compose**
- **Shizuku API** for privileged operations
- **kotlinx.serialization** + SharedPreferences for persistence
- Clean architecture: Repository → ViewModel → UI
- Reactive state via `StateFlow`

## Features

- 4×4 grid UI with implicit add tile
- Suspend and unsuspend apps through Shizuku, with an optional disable fallback
- Frozen state survives app restart and device reboot
- Syncs UI with OS truth-source on launch (handles external state changes)
- Safety filters exclude many sensitive targets, including known system packages and launchers
- One-time first-launch onboarding
- Smart Shizuku status banner with one-tap restart

## Setup

1. Install [Shizuku](https://shizuku.rikka.app/) (free, open source — F-Droid or GitHub)
2. Start Shizuku via wireless debugging (one-time per device reboot, ~30 seconds)
3. Install Freezer (APK in [Releases](../../releases))
4. Open Freezer, grant Shizuku permission when prompted
5. Tap `+`, pick an app, watch it freeze

## Known limitations

- **Battery savings are not yet measured.** Suspension does not guarantee that background activity stops.
- **Launcher behavior varies.** Suspended apps usually stay visible, but launchers control their appearance. Disabled apps may disappear or lose their previous position when re-enabled.
- **Shizuku must be restarted after every device reboot.** Inherent to non-rooted Shizuku.
- **WhatsApp/messaging app callers see "ringing", not "unreachable".** Server-side state, outside any user-space freezer's control.
- **Not on Play Store.** Google policy restricts apps in this category. Distributed via GitHub Releases.

## Building from source

```bash
git clone https://github.com/Samantha-fd/freezer.git
cd freezer
# Open in Android Studio (Hedgehog or newer)
# Sync Gradle, build, run
```

Requires Android Studio Hedgehog+, Android SDK 26+ for runtime, SDK 36 for build.

For distribution, use `freezer-v<version>-release.apk` for a release-signed APK and `freezer-v<version>-debug.apk` for a debug-signed test build. Android updates require the same application ID and compatible signing certificate as the installed app. Keep the release signing key private and backed up.

## License

[MIT](LICENSE) — free to use, modify, distribute. Attribution appreciated.

## Built with

This was my first Kotlin/Android project — every line was hand-typed and understood.

---

*Inspired by missing a feature from an older phone.*