# AGENTS.md

AutoTask (自动化助手) — Android automation app (GKD-style rules: click/input/swipe driven by accessibility-node selectors). Single Gradle module, Kotlin + Jetpack Compose, package `com.yzjdev.autotask`, rootProject `AutoTask`. Developed and built on-device via Termux (project lives on `/storage/emulated/0`).

## Build
- `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` (debug uses `.debug` applicationId suffix, coexists with release)
- `./gradlew :app:assembleRelease` — signed with `app/release.keystore` (credentials inline in `app/build.gradle.kts`)
- No tests, no lint/ktlint config, no CI. Do not build/install/verify unless the user explicitly asks.
- Toolchain: Gradle wrapper 9.6.1, AGP 9.1.1, Kotlin 2.2.10, compileSdk 36 / minSdk 26. All versions live in `gradle/libs.versions.toml` only.
- material3 1.4.0: `SmallTopAppBar` and `SearchBarDefaults.MinHeight` no longer exist; use `PrimaryTabRow`/`SecondaryTabRow` (plain `TabRow` is deprecated); LazyColumn `items()` has no `label` parameter.

## Architecture
- `MainActivity.kt` (~5.3k lines) holds ALL Compose UI: AutomationScreen, HomeScreen, rule editor, permission cards, plus `RulesDocPage` — a hand-rolled markdown parser (`RulesDocBlocks`) rendering `app/src/main/assets/rules-guide.md`. Changing that asset or doc rendering means touching this parser.
- `automation/` is the engine: `DramaAccessibilityService` (event source + gesture executor) → `TaskRunner` (matching/scheduling, coroutine-based) → `GkdSelector` (GKD selector-language parser, semantics aligned with gkd-li/gkd) and `GkdTask` (serializable rule model + expression evaluator). `GkdSubscription`/`SubscriptionFetcher` import GKD JSON5 subscriptions; `ShizukuShell` runs privileged shell via Shizuku.
- The accessibility service is registered in the manifest under the decoy system name `com.google.android.accessibility.selecttospeak.SelectToSpeakService` (11-line empty subclass) to bypass apps that verify the service package; all logic stays in parent `DramaAccessibilityService`. Don't "fix" this.
- `crash/` runs in a separate `:crash` process so the crash viewer survives main-process death.
- Persistence = SharedPreferences + kotlinx.serialization, versioned keys (`gkd_rules_v5`, `gkd_subscriptions_v1`). Old formats are never migrated — a schema change means a NEW key. `encodeDefaults = true` so editor round-trips don't drop params.

- Install on device: `~/install-app.sh --debug` (builds, then launches the system installer via `am start` — you confirm on screen). Plain `adb install` gets `INSTALL_FAILED_ABORTED` here.

## Theming
`ui/theme/` is static: `Color.kt` hardcodes one brand scheme (violet primary / cyan secondary / rose tertiary) and `AutoTaskTheme` switches light/dark off `isSystemInDarkTheme()`, with dynamic color hardcoded off. There is no user-facing theme setting. If you add one, note that `android.graphics.Color` and `androidx.core.graphics.ColorUtils` are stubs under JVM unit tests (throw "not mocked") — do color math in pure Kotlin if it needs to be testable.

## Gotchas
- Rules default `enabled = false` (imported subscription rules AND locally created ones); the user enables them in the UI. Preserve this.
- Overlays (`OverlayDebugWindow`/`OverlayToggleWindow`/`OverlayWindowListWindow`) use `TYPE_ACCESSIBILITY_OVERLAY` — no `SYSTEM_ALERT_WINDOW` permission is needed; don't add one. Each is toggled from the home screen (`show*Overlay` on the service) and auto-hidden on unbind/destroy.
- Manifest permission blocks document the reasoning (WRITE_SECURE_SETTINGS must be declared or Shizuku's `pm grant` fails silently; QUERY_ALL_PACKAGES required for package visibility on Android 11+). Read before touching permissions.
- `shizuku-aidl` is required for `newProcess`; `shizuku-api` + `provider` alone don't include the IShizukuService stubs.
- Comments are in Chinese; keep them terse, no change-history narration.

## Maintenance
When build/test commands, structure, architecture boundaries, versions, or conventions documented here change, update this file in the same change.
