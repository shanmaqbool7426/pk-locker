---
kind: logging_system
name: Android `android.util.Log`-based ad-hoc logging with domain-tagged messages
category: logging_system
scope:
    - '**'
source_files:
    - app/src/main/java/com/pksafe/lock/manager/MainActivity.kt
    - app/src/main/java/com/pksafe/lock/manager/util/LockManager.kt
    - app/src/main/java/com/pksafe/lock/manager/receiver/AdminReceiver.kt
    - app/src/main/java/com/pksafe/lock/manager/service/LockService.kt
    - app/src/main/java/com/pksafe/lock/manager/service/MyFirebaseMessagingService.kt
    - app/src/main/java/com/pksafe/lock/manager/util/AdbSocketEngine.kt
    - app/src/main/java/com/pksafe/lock/manager/util/AutoUpdater.kt
    - app/src/main/java/com/pksafe/lock/manager/ui/dashboard/DashboardViewModel.kt
---

## What system/approach is used

The app uses the platform-provided `android.util.Log` class exclusively for logging. There is no third-party logging framework (no Timber, SLF4J, Logback, Log4j, Crashlytics logger, or similar) declared in `app/build.gradle.kts` or `gradle/libs.versions.toml`. All log output goes through `Log.d`, `Log.w`, and `Log.e` calls scattered across every major component.

## Key files and packages

Logging is not centralized — it is embedded directly in the code that performs work:

- `MainActivity.kt` — startup, token sync, EMI lock sync, auto IMEI polling, deep-link handling
- `receiver/AdminReceiver.kt`, `receiver/AdminPolicyComplianceActivity.kt`, `receiver/SmsReceiver.kt`, `receiver/SimStateReceiver.kt`, `receiver/GetProvisioningModeActivity.kt` — device-admin lifecycle and SMS-based provisioning
- `service/LockService.kt`, `service/AntiUninstallService.kt`, `service/ConnectivityWorker.kt`, `service/MyFirebaseMessagingService.kt` — foreground lock service, anti-uninstall guard, connectivity worker, FCM handler
- `util/LockManager.kt`, `util/AdbSocketEngine.kt`, `util/ApkServer.kt`, `util/AutoUpdater.kt`, `util/UsbAdbEngine.kt`, `util/UpdateReceiver.kt` — core enforcement and ADB engine
- `ui/dashboard/DashboardViewModel.kt`, `ui/deregister/DeregisteredListViewModel.kt`, `ui/devices/DeviceListViewModel.kt`, `ui/registration/RegistrationViewModel.kt` — UI layer ViewModels
- `worker/LocationWorker.kt` — background location reporting

There is no dedicated logging module, no central logger singleton, and no log-level configuration file.

## Architecture and conventions

1. **Tag-per-domain convention.** Every call uses a short uppercase string tag that identifies the logical subsystem rather than the class name. Examples observed: `LOCK_MANAGER`, `LOCK_ENFORCE`, `UNLOCK_ENFORCE`, `SYNC_TOKEN`, `LOCK_SYNC`, `SMS_CODES`, `AUTO_IMEI`, `ADMIN_RECEIVER`, `POLICY_COMPLIANCE`, `MAIN_ACTIVITY`, `STARTUP_REFRESH`, `SECURITY_ENFORCE`, `LOCATION_SYNC`, `PAYMENT_LINK`. This makes filtering by feature area possible via `logcat -s TAG`.

2. **Level usage pattern.**
   - `Log.d` is used for normal operational flow (e.g., "Accessibility service enabled via DPM setSecureSetting", "StatusBar expansion restricted", "Shopkeeper token synced", "IMEI auto-fetched").
   - `Log.w` is used for non-fatal warnings or degraded paths (e.g., "Server response unsuccessful or body null", "Network error fetching SMS codes — SmsReceiver will use IMEI-based generation").
   - `Log.e` is used for failures, typically wrapping an exception as the third argument (e.g., `Log.e("LOCK_MANAGER", "Admin request failed", e)`; `Log.e("SYNC_TOKEN", "Failed to sync shopkeeper token: ${e.message}")`).

3. **Structured-ish fields via string interpolation.** Messages embed key context values inline (IMEI, shop name, phone, EMI amount, response code, error message). There is no structured JSON payload or separate field API — the structure lives entirely in the human-readable message template.

4. **No build-type gating.** The same source emits debug/warn/error logs in both `debug` and `release` build types. The `debug` build type only differs from release by using the release signing config; there is no `isDebuggable` or `BuildConfig.DEBUG` check around `Log` calls.

5. **Error propagation alongside logging.** Exceptions are caught locally and logged with `Log.e(..., e)` rather than rethrown, so each component handles its own failure reporting.

6. **External artifacts.** The repository root contains several plain-text log dumps (`provisioning_debug.log`, `qr_debug_log.txt`) and Gradle build outputs (`build_output*.txt`, `build_log.txt`) that serve as ad-hoc log archives, but these are generated outside the app process.

## Conventions and constraints

- **Observed convention:** Use a single uppercase domain tag per subsystem and pass the most relevant contextual identifiers (IMEI, shop name, phone, response code) inline in the message.
- **Observed convention:** Wrap exceptions as the third parameter of `Log.e` so stack traces are captured automatically by Android's log sink.
- **Observed constraint:** No external logging library is available; all output goes through `android.util.Log`, which routes to the Android logcat sink. There is no custom sink, file writer, or remote log exporter implemented in this codebase.
- **Observed constraint:** There is no centralized log level configuration; all three levels (`d`, `w`, `e`) are emitted unconditionally regardless of build variant.