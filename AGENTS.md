# Repository Guidelines

## Project Structure & Module Organization

PetBlud is a single-module native Android project. Application code lives in `app/src/main/java/com/skysplatwoomy/petblud/`. `PetOverlayService` owns overlay windows and sensors, while `PetManager` contains platform-independent physics and mixed-species state. Dashboard activities/fragments and overlay views remain separate from that core.

Android resources are under `app/src/main/res/`: layouts in `layout/`, vectors in `drawable/`, unscaled sprite sheets in `drawable-nodpi/`, and theme strings/colors in `values/`. JVM tests live in `app/src/test/`; device and UI tests live in `app/src/androidTest/`. Build evidence and licensing constraints are documented in `BUILD-VERIFICATION.md` and `NOTICE-UPSTREAM.md`.

## Build, Test, and Development Commands

- `./gradlew test` — run debug and release JVM tests.
- `./gradlew lint` — run Android lint; warnings are treated as errors.
- `./gradlew assembleDebug` — build `app/build/outputs/apk/debug/app-debug.apk`.
- `./gradlew assembleAndroidTest` — compile the instrumentation APK.
- `./gradlew test lint assembleDebug assembleAndroidTest` — required full verification gate.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — install on an attached device.

Use JDK 17 and Android SDK 36. Device acceptance should cover API 26, 31, and 34+.

## Coding Style & Naming Conventions

Follow standard Kotlin style: four-space indentation, trailing commas in multiline declarations, `PascalCase` types, `camelCase` functions/properties, and `UPPER_SNAKE_CASE` constants. Keep Android resource names lowercase `snake_case`. Prefer small immutable data classes and keep physics logic independent of Android framework classes. Match existing Material XML patterns and maintain 48 dp minimum touch targets.

## Testing Guidelines

Use JUnit 4 and `kotlinx-coroutines-test` for JVM tests; use AndroidX Test and Espresso for instrumentation. Name tests as descriptive lower-camel behavior statements, such as `crossSpeciesCollisionUsesOnlyHootsPageToggle`. Every behavior change needs focused regression coverage. Overlay pass-through, sensors, keyboard bounds, rotation, and screen-off behavior also require device testing.

## Commit & Pull Request Guidelines

History currently has one release-style initial commit, so no strict convention exists. Use concise imperative subjects, for example `Fix Hoots keyboard bounds`. Keep commits focused. Pull requests should explain behavior changes, list verification commands, link relevant issues, and include screenshots or recordings for UI/overlay changes. Call out device coverage and any remaining untested API levels.

## Security & Assets

Never commit `local.properties`, credentials, build directories, or generated APKs. Keep this repository private: redistribution rights for the Seedy and Hoots assets are unresolved. If privileged local setup is ever necessary, use `pkexec`, not `sudo`.
