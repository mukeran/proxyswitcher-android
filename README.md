# ProxySwitcher Android

Android companion implementation for the iOS ProxySwitcher tweak.

It provides:

- a native Android app for saving HTTP proxy profiles;
- a Quick Settings tile for switching between Direct and the last active proxy;
- root-backed application of the device HTTP proxy with `settings put global http_proxy`.

## Build

If Android Gradle Plugin is available:

```sh
gradle assembleDebug
```

This repository also includes an offline SDK build script that uses the local Android SDK directly:

```sh
./scripts/build-debug.sh
```

The APK is written to `build/outputs/apk/debug/proxyswitcher-debug.apk`.

## Usage

Install the APK on a rooted Android device. Open ProxySwitcher, add one or more profiles, then tap `Direct` or a profile to apply it. Add the `ProxySwitcher` tile from Android Quick Settings for fast switching.

Android Quick Settings tiles cannot expose the same expanded custom action list as an iOS Control Center module. The tile mirrors the iOS tap behavior: when Direct, tapping applies the last used proxy or the first saved profile; when a proxy is active, tapping switches back to Direct.
