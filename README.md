# ProxySwitcher Android

Android companion implementation for the iOS ProxySwitcher tweak.

It provides:

- a native Android app for saving HTTP proxy profiles;
- a Quick Settings tile dialog for selecting Direct / Profile / Wi-Fi;
- an LSPosed module that applies the current Wi-Fi HTTP proxy inside the Android framework process.

## Build

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
