---
kind: error_handling
name: Ad-hoc try/catch with Log.e and UI errorMessage State in an Android App
category: error_handling
scope:
    - '**'
source_files:
    - app/src/main/java/com/pksafe/lock/manager/ui/login/LoginViewModel.kt
    - app/src/main/java/com/pksafe/lock/manager/ui/dashboard/DashboardViewModel.kt
    - app/src/main/java/com/pksafe/lock/manager/ui/devices/DeviceListViewModel.kt
    - app/src/main/java/com/pksafe/lock/manager/util/LockManager.kt
    - app/src/main/java/com/pksafe/lock/manager/MainActivity.kt
    - app/src/main/java/com/pksafe/lock/manager/receiver/AdminReceiver.kt
    - app/src/main/java/com/pksafe/lock/manager/receiver/SmsReceiver.kt
    - app/src/main/java/com/pksafe/lock/manager/receiver/SimStateReceiver.kt
    - app/src/main/java/com/pksafe/lock/manager/service/AntiUninstallService.kt
    - data/ApiService.kt
---

## What system/approach is used

The PKLocker Android app has **no centralized error-handling framework, no custom exception types, no error monads, and no middleware**. Errors are handled locally with ad-hoc `try { ... } catch (e: Exception) { ... }` blocks that log via `android.util.Log.e(...)` and, in the UI layer, surface failures through a plain `String?` state field called `errorMessage`. Network errors come from Retrofit's `Response.isSuccessful` checks; device-admin / telephony calls are wrapped in per-call try/catch blocks. There is no global uncaught-exception handler, no `CoroutineExceptionHandler`, and no structured logging library.

## Key files and packages

- **UI ViewModels** — centralize user-facing error state:
  - `app/src/main/java/com/pksafe/lock/manager/ui/login/LoginViewModel.kt` — sets `errorMessage = "Connection Failed: Check your server"` on network exceptions; also catches FCM token update failures.
  - `app/src/main/java/com/pksafe/lock/manager/ui/dashboard/DashboardViewModel.kt` — sets `errorMessage = "Connection Failed"` and logs `DASHBOARD_VM`.
  - `app/src/main/java/com/pksafe/lock/manager/ui/devices/DeviceListViewModel.kt` — every API call (`fetchDevices`, `fetchEmiSchedule`, `markEmiAsPaid`, `rescheduleEmiPlan`, `toggleLock`, `sendControl`, `unlockAllControls`, `deregisterDevice`) follows the same pattern: `try { ... } catch (e: Exception) { Log.e("TAG", e.message); errorMessage = "Connection Failed" } finally { isLoading = false }`.
- **Core enforcement utility** — `app/src/main/java/com/pksafe/lock/manager/util/LockManager.kt` — wraps every DevicePolicyManager / Settings / Ringtone / Wallpaper / Telephony call in its own try/catch, logging under the `LOCK_MANAGER` tag. Includes a deliberate fallback path for enabling the Accessibility service (DPM `setSecureSetting` → `Settings.Secure` direct write).
- **Android entry points** — `MainActivity.kt`, `receiver/AdminReceiver.kt`, `receiver/SmsReceiver.kt`, `receiver/SimStateReceiver.kt`, `service/AntiUninstallService.kt` — each catch `Exception` around IMEI fetching, SMS parsing, permission checks, and service start/stop, typically falling back to `null` or empty string and logging.
- **Data contract** — `data/ApiService.kt` defines Retrofit `suspend` functions returning `Response<T>`; it declares no custom error types. Error handling lives entirely in callers.
- **Build-time Kotlin errors** — `.kotlin/errors/*.log` files capture Gradle/Kotlin compile daemon crashes (e.g., `Could not connect to Kotlin compile daemon`). These are IDE/Gradle artifacts, not runtime application errors.

## Architecture and conventions

1. **Per-call local try/catch**: Every potentially failing call — whether Retrofit, `TelephonyManager.getImei()`, `DevicePolicyManager.setUserRestriction()`, `startForegroundService()`, or `URL.openConnection()` — is individually wrapped in `try { ... } catch (e: Exception) { ... }`. There is no shared helper function; each site decides what to do.
2. **Logging via `Log.e` / `Log.w` / `Log.d`**: Failures are recorded with a short tag (`MAIN_ACTIVITY`, `SYNC_TOKEN`, `LOCK_ENFORCE`, `LOCK_MANAGER`, `API_ERROR`, `EMI_FETCH_ERROR`, etc.) plus `e.message` or the full exception. No structured logger is used.
3. **User-visible errors as plain strings**: The UI layer exposes a `var errorMessage by mutableStateOf<String?>(null)` in each ViewModel. On success the field is cleared; on failure it is set to a human-readable message such as `"Connection Failed"`, `"Authentication required"`, `"Failed to fetch stats: ${response.code()}"`, or `"Invalid credentials"`. Screens read this state to show snackbar/toast-like feedback.
4. **Silent fallbacks for non-fatal OS calls**: Calls that may fail due to missing permissions or unsupported APIs (IMEI access, status bar disabling, wallpaper setting) return a default value (`null`, `false`, empty list) rather than propagating the exception. This keeps the lock flow resilient even when parts of the device policy stack are unavailable.
5. **Retrofit response inspection, not exceptions**: Network errors are treated as business logic: callers check `response.isSuccessful && response.body()?.success == true` and branch accordingly. Only transport-level exceptions bubble up into the `catch` block.
6. **No global error boundary**: There is no `Thread.setDefaultUncaughtExceptionHandler`, no `CoroutineExceptionHandler`, no `@Throws` declarations, and no custom `AppError` sealed class. Errors are consumed at the call site.

## Conventions and constraints observed

- **Every ViewModel method that calls the API follows the same shape**: set `isLoading = true`, clear `errorMessage`, launch a `viewModelScope` coroutine, wrap the call in `try { ... } catch (e: Exception) { ... }`, then `finally { isLoading = false }`. This is consistent across `LoginViewModel`, `DashboardViewModel`, and `DeviceListViewModel`.
- **Device-policy operations guard themselves with capability checks before attempting the call** (e.g., `if (!isDeviceOwner()) return` before calling `setUserRestriction`), so many exceptions are prevented proactively rather than caught reactively.
- **Fallback chains are explicit**: `LockManager.ensureAccessibilityServiceEnabled()` tries the enterprise DPM path first, then falls back to raw `Settings.Secure` writes, logging both attempts.
- **Non-critical failures swallow exceptions silently** (e.g., `catch(e: Exception) { null }` for IMEI retrieval, `catch(_ : Exception) { "" }` for package name lookup). This is a deliberate resilience choice for a device-locking app where partial data must still allow the lock to proceed.
- **No custom error types exist**: The codebase does not define domain-specific exception classes, result wrappers, or error codes. All errors are represented as either Retrofit `Response` status codes, thrown `Exception`s caught locally, or plain `String?` messages.