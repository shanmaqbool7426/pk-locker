---
kind: dependency_management
name: Gradle Version Catalog–Based Android Dependency Management
category: dependency_management
scope:
    - '**'
source_files:
    - gradle/libs.versions.toml
    - settings.gradle.kts
    - app/build.gradle.kts
    - build.gradle.kts
    - gradle.properties
---

## System / Approach

The project is a Gradle-based Android multi-module build (root + `:app` module) that centralizes all third-party dependency versions and plugin versions in a **Gradle Version Catalog** (`gradle/libs.versions.toml`). Module-level `build.gradle.kts` files declare dependencies only by catalog aliases via the `alias(libs.*)` syntax, so version numbers live in one place.

Repository-wide dependency resolution is enforced centrally through `settings.gradle.kts`, which:
- Declares the repository order: `google()`, `mavenCentral()`, plus `gradlePluginPortal()` for plugins under `pluginManagement`.
- Sets `dependencyResolutionManagement.repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)`, which **forbids per-module `repositories { ... }` blocks**, ensuring every artifact must be resolved from the root-declared repos.
- Uses the `org.gradle.toolchains.foojay-resolver-convention` plugin to auto-resolve the JDK toolchain.

No vendoring or private Maven registry is used; all artifacts are pulled from Google's Maven repo and Maven Central.

## Key Files

- `gradle/libs.versions.toml` — single source of truth for all library versions, library aliases, and plugin versions/IDs.
- `settings.gradle.kts` — global repository configuration, version-catalog wiring, and enforcement of `FAIL_ON_PROJECT_REPOS`.
- `app/build.gradle.kts` — the only application module; declares all runtime/test/debug dependencies using `libs.<alias>` references.
- `build.gradle.kts` (root) — applies top-level plugins with `apply false`; no module-specific dependency declarations.
- `gradle.properties` — enables AndroidX (`android.useAndroidX=true`) and Jetifier (`android.enableJetifier=true`), sets JVM args.
- `app/google-services.json` — Firebase configuration consumed by the `com.google.gms.google-services` plugin declared in the catalog.

## Architecture and Conventions

1. **Version Catalog as the single source of truth.** Every dependency version is defined under `[versions]` and referenced via `version.ref = "..."`. Library aliases under `[libraries]` group related artifacts (e.g., `retrofit` and `retrofit-gson` share the same Retrofit version). BOMs (`composeBom`, `firebaseBom`) are used to manage transitive versions within those ecosystems.
2. **Plugin versions also centralized.** All four plugins (`android-application`, `kotlin-android`, `kotlin-compose`, `google-services`) are declared in the `[plugins]` section and applied via `alias(libs.plugins.*)` in both the root and app modules.
3. **Module builds depend on aliases, not coordinates.** The app module never hardcodes a group/name/version triple for its own dependencies; it only calls `implementation(libs.<alias>)`. This makes cross-module upgrades a single edit in `libs.versions.toml`.
4. **Compose and Firebase use BOMs.** Compose UI components are imported through `platform(libs.androidx.compose.bom)` and Firebase libraries through `platform(libs.firebase.bom)`, letting individual modules opt into specific features without pinning each transitive version.
5. **Play Services split between catalog and inline versions.** Most Play Services artifacts (`play-services-code-scanner`, `play-services-location`, `play-services-maps`) are listed in the catalog; a few are pinned directly in the app module's `dependencies` block (e.g., `play-services-location` at `21.3.0`, `play-services-maps` at `19.0.0`, `maps-compose` at `6.1.2`). These represent minor deviations from the fully catalogized pattern.
6. **Test vs. production scoping.** Test-only dependencies (`junit`, `androidx.junit`, `espresso-core`, `compose-ui-test-junit4`) are scoped with `testImplementation` / `androidTestImplementation`, while debug-only tooling (`compose-ui-tooling`, `compose-ui-test-manifest`) uses `debugImplementation`.
7. **Signing and packaging are build-time concerns, not dependency concerns.** Keystore files (`prod.keystore`, `release.keystore`, `release2.keystore`, `release3.keystore`) and ProGuard rules live alongside the app module but do not affect dependency resolution.

## Conventions and Constraints

- **All dependency versions must be declared in `gradle/libs.versions.toml`**; adding a new library means defining an entry in `[versions]` and an alias in `[libraries]`, then referencing it via `libs.<alias>` in the app module.
- **Per-module `repositories { }` blocks are forbidden.** `settings.gradle.kts` enforces this with `RepositoriesMode.FAIL_ON_PROJECT_REPOS`; any module attempting to declare its own repositories will fail the build.
- **Only two artifact sources are allowed:** `google()` and `mavenCentral()` (plus `gradlePluginPortal()` for plugins). No local file-based repositories or custom/private registries are configured.
- **AndroidX is mandatory.** `gradle.properties` sets `android.useAndroidX=true` and `android.enableJetifier=true`, so legacy support libraries are expected to be migrated to AndroidX.
- **Compile/target SDK and Java/Kotlin targets are pinned at the module level:** `compileSdk = 35`, `targetSdk = 35`, `minSdk = 24`, `sourceCompatibility = JavaVersion.VERSION_11`, `jvmTarget = "11"`, and Kotlin `2.0.21`.
- **Firebase integration is gated by the `google-services` plugin** (declared in the catalog as `googleServices = "4.4.2"`) and requires the presence of `app/google-services.json`.
- **Build reproducibility relies on the version catalog + BOMs**; there is no Gradle `--refresh-dependencies` lockfile committed to the repo, so exact resolved versions are derived from the declared constraints rather than a checked-in lockfile.