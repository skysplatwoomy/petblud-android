# PetBlud for Android

Native Kotlin port of [skysplatwoomy/petblud](https://github.com/skysplatwoomy/petblud), implemented as a user-enabled system-wide Android overlay.

Current build: **0.5.0**.

## What is implemented

- One foreground service and one mixed-species manager for every pet.
- Seedy and Hoots can roam together. Seedys retain their 100-pet cap; Hoots is limited to one and repeated wake requests are idempotent.
- Transparent, independently touchable `TYPE_APPLICATION_OVERLAY` windows, so touches outside a pet still reach the app underneath.
- The original 11 animation rows, frame counts, and frame rates.
- The original gravity, bounce, throw, hard-landing, idle-emote, explosion, and collision behavior.
- Tap to hop, drag to throw, and long-press for a service-owned popover that never switches away from the app underneath.
- A dark Material dashboard with live per-species status, contextual controls, and accessible 48 dp targets.
- A pinned `petblud` header that stays visible while the dashboard scrolls and respects status-bar, cutout, and navigation-bar insets.
- Horizontally swipeable Seedy and Hoots pages with visible tabs, green Seedy accents, and orange Hoots accents.
- Fully separate gravity, motion, keyboard avoidance, physics, and size settings through Preferences DataStore. Existing unprefixed settings remain Seedy's migration-compatible keys; Hoots uses `hoots_` keys.
- Optional Seedy-to-Hoots collision, off by default and available only on the Hoots page. Seedy-to-Seedy collision remains independent.
- Optional tilt-directed gravity using Android's gravity sensor, with an accelerometer fallback.
- Optional shake detonation under tilt gravity. Linear shake acceleration scales the existing detonation force from 1.5× to 4×, with a 700 ms cooldown.
- Optional gravity-relative sprite rotation under tilt gravity, with enlarged overlay bounds to avoid clipping rotated Seedys.
- Optional keyboard avoidance that keeps Seedys above the visible software keyboard on API 30+.
- Species-aware per-pet gravity and physics controls inside green Seedy or orange Hoots overlay popovers.
- Species-scoped spawn, explode, detonate, remove-this, remove-others, and remove-all actions. The notification's `Stop all` action remains global.
- Screen-off physics/animation pause and display-bound recalculation after configuration changes.
- Android 14+ `specialUse` foreground-service declaration and an ongoing notification.
- A hard cap of 100 Seedys plus one Hoots.

## Build

The project uses Android Gradle Plugin 8.13.2, Gradle 8.13, JDK 17, and Android SDK 36.

```sh
./gradlew test lint assembleDebug assembleAndroidTest
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open PetBlud, grant **Display over other apps**, optionally grant notification access, then wake Seedy or swipe to the Hoots page.

## Controls

- Tap either pet: hop.
- Drag and release: throw with momentum.
- Long-press: open pet actions and per-pet physics controls in a compact overlay card.
- Tap outside the long-press card: close it and continue with the app underneath.
- App screen: swipe between independently live-saving Seedy and Hoots controls.
- Ongoing notification: `+ Seedy`, `Wake Hoots`, or `Stop all`.

## Android behavior and limitations

- The overlay permission is special access and must be granted in system settings. The app does not attempt to bypass that screen.
- A foreground service is used because a persistent overlay must remain user-visible to Android. Android 13+ can stop it from the system's Active apps UI.
- Android 12+ blocks unsafe touch pass-through through untrusted overlays. Each pet therefore uses a small touchable window instead of one full-screen transparent touch layer.
- Some OEM battery managers can still stop or hide background overlays. There is no boot receiver or automatic restart after reboot.
- The long-press menu is another small `TYPE_APPLICATION_OVERLAY` window owned by the service. It is non-focusable and non-touch-modal, uses outside-touch dismissal, and permits only one menu at a time.
- API 26–29 use display metrics for bounds. API 30+ also excludes system-bar and display-cutout insets.
- Tilt gravity, shake detonation, gravity-relative sprite rotation, keyboard avoidance, and cross-species collision are disabled by default. Motion options and sensor reactions are independently routed per species. If both pets enable shake, the same detected shake launches both using its measured strength.
- Reliable IME visibility insets are available to this overlay on API 30+. Keyboard avoidance is therefore best-effort on API 26–29 and does not shrink the play area there.

## Verification

The JVM suite contains 29 tests per build type. It covers the existing physics and UI logic plus mixed-species coexistence, globally unique IDs, the one-Hoots limit, species-scoped chaos/removal, independent settings/bounds/gravity, cross-collision gating, Hoots dashboard copy, and per-species sensor routing.

An instrumentation APK compiles three device tests for controls/accessibility, pinned-header hierarchy, pager order, Seedy/Hoots labels, orange Hoots theming, and Hoots-only collision controls. Those tests require a device or emulator to execute.

Device acceptance still requires an Android device or emulator with overlay access. Test API 26, API 31, and API 34 or newer, including rotation, screen-off/resume, notification stop, and touches outside every pet window.

## Upstream and licensing

The Seedy behavior and images were taken from upstream commit `f230695232a9e4abc410e534bae8c24fea0177b5`. The Hoots sheet was imported from the local Codex desktop asset bundle and is recorded in the verification report.

The upstream repository does not currently contain a `LICENSE`, `COPYING`, or `NOTICE` file. Do not redistribute the upstream code or images until the copyright holder grants permission or publishes a license. See [NOTICE-UPSTREAM.md](NOTICE-UPSTREAM.md).
