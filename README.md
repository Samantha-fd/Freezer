# Freezer

An Android app freezer that disables apps at the OS level without uninstalling them. Uses [Shizuku](https://shizuku.rikka.app/) for privileged access — no root required.

Frozen apps:
- Stop running in the background
- Stop receiving notifications
- Stop draining battery
- Keep all their data — restored instantly on unfreeze

## Why

Most app freezers on the Play Store don't actually work. Android's security model only allows OS-level processes (or apps with root, device-owner, or ADB privileges) to disable other apps. Consumer freezer apps fake it with battery restrictions and force-stops that Android reverses within minutes.

Freezer uses Shizuku as a privilege bridge to call Android's real `pm disable-user` API — the same mechanism the OS itself uses.

## Tech stack

- **Kotlin** + **Jetpack Compose**
- **Shizuku API** for privileged operations
- **kotlinx.serialization** + SharedPreferences for persistence
- Clean architecture: Repository → ViewModel → UI
- Reactive state via `StateFlow`

## Features

- 4×4 grid UI with implicit add tile
- Real OS-level freeze/unfreeze via Shizuku
- Frozen state survives app restart and device reboot
- Syncs UI with OS truth-source on launch (handles external state changes)
- Safety filters block dangerous targets (system apps, Google services, WebView, launchers, authenticators)
- One-time first-launch onboarding
- Smart Shizuku status banner with one-tap restart

## Setup

1. Install [Shizuku](https://shizuku.rikka.app/) (free, open source — F-Droid or GitHub)
2. Start Shizuku via wireless debugging (one-time per device reboot, ~30 seconds)
3. Install Freezer (APK in [Releases](../../releases))
4. Open Freezer, grant Shizuku permission when prompted
5. Tap `+`, pick an app, watch it freeze

## Known limitations

- **Battery savings are not yet measured.** Working on it — next phase wires up Android's `BatteryStats` API.
- **App icon position resets after unfreeze.** When an app is disabled and re-enabled, your launcher treats it as a fresh install. Stock launchers (Pixel, Samsung) don't preserve original positions; some third-party launchers (Nova, Lawnchair) do.
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

## License

[MIT](LICENSE) — free to use, modify, distribute. Attribution appreciated.

## Built with

This was my first Kotlin/Android project. Paired with [Claude](https://claude.com) (Anthropic) as a learning coach throughout — every line was hand-typed and understood.

---

*Inspired by missing a feature from an older phone.*