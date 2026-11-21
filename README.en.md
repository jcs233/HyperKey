# HyperKey

[简体中文](README.md) · [Device validation (Chinese)](docs/VALIDATION.md)

HyperKey is an LSPosed module for physical keyboards on Xiaomi HyperOS tablets. It adds controls to both the generic physical keyboard settings and the Xiaomi keyboard’s own shortcuts page. For example, you can keep `Win+D` as “go home” on the tablet while passing the Win key through to a remote desktop client only when its window has keyboard focus.

**Tested configuration:** Xiaomi Pad 8 Pro (`piano`), HyperOS `OS4.0.5.0.XPYCNXM`, Android 17 (API 37), LSPosed 2.2.1 (7944), and System Security Components `4.4.4.3-4-260914.pad`. The module targets libxposed API **102.0.0**. Other firmware may implement keyboard shortcuts differently.

## Build and install

**[Download HyperKey 0.3.2 (2.33 MiB)](https://github.com/jcs233/HyperKey/releases/download/v0.3.2/HyperKey-0.3.2.apk)** · [Release notes and SHA-256 checksum](https://github.com/jcs233/HyperKey/releases/tag/v0.3.2)

- To block three-finger swipe-down screenshots on the physical touchpad, turn on the switch in HyperKey or either keyboard settings page. It is off by default, independent of the keyboard master switch, and leaves the touchscreen and keyboard screenshot settings unchanged.
- The [108-key picker](docs/assets/keyboard-108.svg) now includes Fn and can map chords with multiple ordinary keys and modifiers on both input and output. For `A+B+C`, hold A and B, then press C. The app still receives the companion keys. A firmware-only Fn layer can only be bound by the translated key Android receives.

Version 0.3.2 passed 40 unit tests. The tablet results above are from earlier versions; actual Fn reporting, multi-key mappings and touchpad screenshot blocking still need verification on the target tablet.

**Install or upgrade:** This release uses the same certificate as the 0.3.0 release and project test builds.

| Currently installed | How to upgrade |
| --- | --- |
| Nothing | Open the downloaded APK, then enable the module as described below. |
| The GitHub 0.3.0 release, the project's 0.3.1 build, or an earlier build with the same certificate | Install over it, or run `adb install -r HyperKey-0.3.2.apk`. Reboot to load the updated system hooks. |
| The GitHub 0.2.0 release, or a 0.2.1 build signed with the old release key | Record any settings you want to keep before uninstalling; uninstalling clears private app data. Install 0.3.2, recheck the three LSPosed scopes, re-grant the permission below if needed, and reboot. |

Self-built debug APKs may use a different key. Compare `apksigner verify --print-certs` output with the certificate fingerprint in the release notes.

To build a debug APK yourself instead, use JDK 17, Android SDK Platform 36 and Android Platform-Tools:

```sh
git clone https://github.com/jcs233/HyperKey.git
cd HyperKey
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

In Windows PowerShell, use `.\gradlew.bat :app:assembleDebug`.

**Enable the module:** Use a working LSPosed installation with API 102 support. Enable HyperKey in LSPosed and select all three scopes: **System Framework** (`system`), **Settings** (`com.android.settings`), and **System Security Components** (`com.miui.securitycore`). Reboot the tablet. Reboot again after an update that changes system hooks.

The native settings pages can write global switches with their host permissions. To edit configuration in the standalone app, including app profiles or key mappings opened from Settings, grant:

```sh
adb shell pm grant io.github.hyperkey android.permission.WRITE_SECURE_SETTINGS
```

## Using the module

- **Global shortcuts:** The master switch disables all 19 supported system shortcuts without erasing their individual settings. The generic and Xiaomi keyboard pages share the global state. The Xiaomi native editor also lets you see the actual bindings if you have changed them.
- **App profiles:** Pick an app by name or icon, or enter its package name. Individual rules can override or inherit global choices. In split screen, keyboard focus determines which profile applies. The global master disable takes precedence.
- **Remapping:** Map one key or chord to another (e.g. Caps Lock → left Ctrl), or add a chord for a system action. A matching app rule takes priority over a global mapping. Master disable suppresses mapped system actions but keeps key-to-key mappings active.
- **Remote desktops and key testing:** Enable Win passthrough in the remote client’s profile. The key tester shows keycaps, Android key codes and recent down/up events. It captures Win on purpose, even when other apps still trigger local shortcuts. Receipt in the tester does not prove receipt by a remote system; end-to-end RDP and Moonlight sessions have not been verified.
- **Launcher icon (0.2.1+):** Open **Language & appearance → Hide launcher icon**. To restore it, reopen HyperKey from LSPosed module settings or **Module management → Open HyperKey** in either keyboard settings page, then turn the switch off.
- **Appearance:** English and Simplified Chinese, system/light/dark themes, wallpaper accents, Material swatches and a custom six-digit HEX color. The controls inside Android Settings keep the host theme.

To stop using HyperKey, disable it in LSPosed and reboot. Individual choices already synced to Xiaomi’s native settings may remain; change them in the native editor if needed. The companion app declares no Internet permission, and the key tester keeps only its last 16 events in memory.

See the [usage guide](docs/USAGE.md), [troubleshooting guide](docs/TROUBLESHOOTING.md), [privacy and permissions](docs/PRIVACY.md), and [contribution guide](CONTRIBUTING.md) (Chinese). This repository does not currently include a project license; see the [third-party APK inventory](app/src/main/assets/third_party/NOTICE.txt) for dependency licenses.
