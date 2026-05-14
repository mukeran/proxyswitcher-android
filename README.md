# ProxySwitcher Android

Android companion implementation for the iOS ProxySwitcher tweak.

It provides:

- a native Android app for saving HTTP proxy profiles;
- a Quick Settings tile dialog for selecting Direct / Profile / Wi-Fi;
- an LSPosed module that applies the current Wi-Fi HTTP proxy inside the Android framework process.
- a non-root VPN mode that applies HTTP proxy via `VpnService` (Android 10+).

## Build

Init submodule (first time only):

```sh
git submodule update --init --recursive
```

`tun2http` now comes from submodule `third_party/proxyswitcher-tun2http`.
Build scripts reuse upstream `scripts/build-android.sh` to produce `.so`, then package as:

- `src/main/jniLibs/arm64-v8a/libtun2http.so`
- `src/main/jniLibs/armeabi-v7a/libtun2http.so`
- `src/main/jniLibs/x86_64/libtun2http.so`

Debug:

```sh
./scripts/build-debug.sh
./scripts/install-debug.sh
```

Release (signed):

```sh
./scripts/build-release.sh
./scripts/install-release.sh
```

Output paths:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release-signed.apk`

## Usage

Install the APK on a rooted Android device. Enable the ProxySwitcher module in LSPosed, scope it to Android/System Framework, then reboot. Open ProxySwitcher, add one or more profiles, then tap `Direct` or a profile to apply it. Add the `ProxySwitcher` tile from Android Quick Settings for fast switching.

In this project, tapping the tile opens a dialog with three sections (`Direct`, `Profiles`, `Wi-Fi`) for explicit switching.

## Modes

Use toolbar menu `Mode` to switch:

- `Root (LSPosed)`: existing behavior. Requires LSPosed module active in Android/System Framework.
- `Non-root (VPN)`: does not require root or LSPosed for applying profile/direct. App requests VPN permission once and keeps a foreground VPN service while proxy is active.

Notes:

- VPN mode depends on `VpnService.Builder#setHttpProxy`, so Android 10+ is required.
- In VPN mode, `Wi-Fi` quick switching is disabled (root-only capability).
