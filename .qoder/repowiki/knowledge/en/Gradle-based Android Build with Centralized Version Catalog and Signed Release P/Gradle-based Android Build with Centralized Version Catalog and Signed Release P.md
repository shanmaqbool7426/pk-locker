---
kind: build_system
name: Gradle-based Android Build with Centralized Version Catalog and Signed Release Pipeline
category: build_system
scope:
    - '**'
source_files:
    - build.gradle.kts
    - settings.gradle.kts
    - app/build.gradle.kts
    - gradle/libs.versions.toml
    - gradle.properties
    - app/google-services.json
    - app/proguard-rules.pro
    - app/prod.keystore
    - README.md
---

## Build System Overview

This is a single-module Android project built entirely with **Gradle (Kotlin DSL)** using the Android Gradle Plugin. There are no Makefiles, Dockerfiles, or CI pipelines in the repository — build orchestration is local via `gradlew`.

## Key Files and Configuration

- **Root build script** (`build.gradle.kts`): Declares plugins with `apply false` for child modules; delegates plugin application to modules.
- **Module build script** (`app/build.gradle.kts`): Defines the Android application module — namespace, SDK targets, signing, build types, Compose features, dependencies, packaging exclusions, and a custom task that deletes a corrupted icon before resource merging.
- **Settings** (`settings.gradle.kts`): Configures `pluginManagement` and `dependencyResolutionManagement` with `RepositoriesMode.FAIL_ON_PROJECT_REPOS` to enforce centralized repo declarations; includes only the `:app` module; uses `org.gradle.toolchains.foojay-resolver-convention` for JDK toolchain resolution.
- **Version catalog** (`gradle/libs.versions.toml`): Single source of truth for all dependency versions (AGP 8.13.2, Kotlin 2.0.21, Compose BOM 2024.11.00, Firebase BOM 33.7.0, Retrofit 2.11.0, etc.) and plugin aliases referenced via `alias(libs.plugins.*)`.
- **Gradle properties** (`gradle.properties`): Sets JVM args (`-Xmx4096m`), enables AndroidX and Jetifier, enforces official Kotlin code style, disables injected test-only flag.
- **Signing keystore**: `prod.keystore` (and `release.keystore`, `release2.keystore`, `release3.keystore`) stored in `app/`; release config uses hardcoded passwords (`pk_locker_123`) and alias `pk_locker`, enabling V1/V2/V3 signing schemes.
- **Google Services**: `google-services.json` in `app/` consumed by the `com.google.gms.google-services` plugin.
- **ProGuard rules**: `proguard-rules.pro` applied in release build type.
- **Helper script**: `app/src/main/new.sh` contains a `keytool` command for listing debug keystore contents (development aid).

## Architecture and Conventions

- **Single-module layout**: Only `:app` is included; shared API contract lives as a plain Kotlin file under `data/ApiService.kt` but is not a Gradle module — it is copied into the app at runtime rather than compiled as a library.
- **Centralized versioning**: All third-party versions live exclusively in `gradle/libs.versions.toml`; modules never pin versions inline.
- **SDK strategy**: `compileSdk = 35`, `targetSdk = 35`, `minSdk = 24`; Java/Kotlin target `VERSION_11` / `jvmTarget = "11"`.
- **Build types**: `debug` reuses the release signing config so Play Protect treats debug builds identically to release; `release` disables minification/shrinking but applies ProGuard rules.
- **Compose-first UI**: `buildFeatures.compose = true` with Material3 and Navigation Compose.
- **Packaging hygiene**: Excludes redundant META-INF license files and previous compilation data from the APK.
- **Resource cleanup hook**: A `tasks.configureEach` block deletes `src/main/res/drawable/app_icon_professional.png` before any `merge*Resources` task runs, working around a known corrupted asset issue.

## Versioning and Artifacts

- Hardcoded in `defaultConfig`: `versionCode = 3`, `versionName = "1.2"`. The README documents an external auto-update flow where the backend `/api/version` endpoint controls force updates based on this `versionCode`.
- Output artifact path: `app/build/outputs/apk/release/app-release.apk`.
- No automated publish step exists in the repo; the README instructs developers to run `.\gradlew.bat assembleRelease` locally after setting `JAVA_HOME`.

## Constraints and Enforced Rules

- Repository-wide dependency sources are locked down: `dependencyResolutionManagement.repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` prevents any subproject from declaring its own repositories, enforcing centralized Maven Central + Google repos.
- Plugins are declared centrally in the version catalog and must be applied via `alias(libs.plugins.*)`; direct `id(...)` plugin declarations are absent from modules.
- Signing credentials are embedded directly in `app/build.gradle.kts` (keystore path, passwords, alias) — no external keystore management or environment variable injection is used.
- Debug builds are forced to use the release signing configuration, ensuring consistent signature behavior across build variants.