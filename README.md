# ProxySwitcher Android

Android companion implementation for the iOS ProxySwitcher tweak.

It provides:

- a native Android app for saving HTTP proxy profiles;
- a Quick Settings tile for switching between Direct and the last active proxy;
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

Android Quick Settings tiles do not support iOS-style long-press action menus. In this project, tapping the tile toggles Direct and active profile, and tapping the tile opens a dialog for explicit profile / Wi-Fi selection.
