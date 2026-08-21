---
kind: configuration_system
name: Android Build-Time & Runtime Configuration via Constants, Gradle, and Google Services
category: configuration_system
scope:
    - '**'
source_files:
    - app/src/main/java/com/pksafe/lock/manager/util/Constants.kt
    - app/build.gradle.kts
    - gradle/libs.versions.toml
    - gradle.properties
    - app/google-services.json
    - app/src/main/res/xml/network_security_config.xml
---

## Overview

This Android project configures the app through a small set of build-time constants, Gradle build configuration, and Google Services. There is no runtime configuration loader (no `.env`, YAML/JSON config files read at startup, or feature-flag framework). Instead, environment-specific values are baked into the APK at build time.

## Build-Time Configuration Sources

### Central Kotlin constant object
- `app/src/main/java/com/pksafe/lock/manager/util/Constants.kt` defines the single source of truth for runtime endpoints:
  - `BASE_URL = "https://pk-locker-api.vercel.app/api/"`
  - `APK_DOWNLOAD_URL = "https://pk-locker-api.vercel.app/apk/update.apk"`
  - A local dev URL (`http://192.168.100.5:5000/api/`) is present as a commented-out alternative, indicating the intended way to switch environments is editing this file.
- Every Retrofit client in the app resolves its base URL from `Constants.BASE_URL` — see usages in `MainActivity.kt`, `ConnectivityWorker.kt`, `LockService.kt`, `DashboardViewModel.kt`, `DeviceListViewModel.kt`, `DeregisteredListViewModel.kt`, `LoginViewModel.kt`, `SignupViewModel.kt`, `RegistrationViewModel.kt`, `BuyKeysViewModel.kt`, `AdminKeyOrdersScreen.kt`, `AutoUpdater.kt`, and `SimStateReceiver.kt`. QR screens (`QrSetupScreen.kt`, `WirelessAdbSetupScreen.kt`) use `Constants.APK_DOWNLOAD_URL` directly.

### Gradle module configuration (`app/build.gradle.kts`)
- `defaultConfig` sets `applicationId`, `minSdk=24`, `targetSdk=35`, `versionCode=3`, `versionName="1.2"`, and resource locale `en`.
- `signingConfigs.release` embeds keystore path and passwords directly in the build script (`prod.keystore`, alias `pk_locker`, password `pk_locker_123`). Both `debug` and `release` build types use this signing config so Play Protect treats debug builds like release builds.
- No `buildTypes` or `productFlavors` exist — there is only one flavor; environment switching is done by editing `Constants.kt`, not via Gradle variants.
- `google-services` plugin is applied via version catalog alias.

### Version catalog (`gradle/libs.versions.toml`)
- Centralizes all dependency versions and library aliases used across modules. This is the only place where third-party library versions are declared.

### Project-wide Gradle properties (`gradle.properties`)
- JVM args, AndroidX flags (`android.useAndroidX=true`, `android.enableJetifier=true`), and test injection flag.

### Google Services (`app/google-services.json`)
- Contains Firebase project metadata for three package names: `com.example.pklocker`, `com.pklocker.enterprise`, and `com.pksafe.lock.manager` (the active application ID). The `google-services` Gradle plugin merges these into generated resources consumed by Firebase SDKs at runtime.

### Network security policy (`app/src/main/res/xml/network_security_config.xml`)
- Declares cleartext HTTP allowed only for private IP ranges (`192.168.0.0/16`, `192.168.100.0/16`, `10.0.0.0/8`, `172.16.0.0/12`), `localhost`, and `127.0.0.1`; all other traffic defaults to HTTPS with system trust anchors. This is how the app configures which networks may be contacted without TLS.

## Architecture & Conventions

1. **Single constant file for external endpoints.** All network addresses flow through `Constants.kt`; callers never hardcode URLs elsewhere. Switching between dev/staging/prod requires editing that file (or swapping it per build variant).
2. **No runtime config loading.** There is no code that reads `.env`, `SharedPreferences` for config, or remote feature flags at startup. Configuration is immutable after the APK is built.
3. **Build-time secrets embedded in Gradle.** Keystore credentials and Firebase API keys live in `app/build.gradle.kts` and `app/google-services.json` respectively — they are compiled into the APK.
4. **Version management via Gradle version catalog.** Dependencies and plugins are referenced by alias (`libs.plugins.*`, `libs.*`) rather than inline version strings.
5. **One build flavor.** The project does not define `flavorDimensions` or multiple product flavors; environment differences are handled by editing source constants rather than Gradle variants.

## Constraints Observed

- Every Retrofit client must obtain its base URL from `Constants.BASE_URL`; adding a new endpoint should reuse this constant rather than introducing a new hardcoded URL.
- Cleartext HTTP is intentionally restricted to the private LAN ranges listed in `network_security_config.xml`; any new domain needing HTTP must be added there explicitly.
- Signing credentials are stored in plaintext inside `app/build.gradle.kts`; the same keystore/password is reused for both debug and release builds.