# Build verification

Verification performed on 2026-08-27 for PetBlud 0.7.0.

## Automated checks

- `testDebugUnitTest`: 40 tests, 0 failures, 0 errors.
- `testReleaseUnitTest`: 40 tests, 0 failures, 0 errors.
- Android lint: no issues found, with warnings treated as errors.
- Debug APK assembly: successful.
- Debug instrumentation APK assembly: successful (6 device tests compiled).
- APK signature verification: valid Android APK Signature Scheme v2 debug signature.

The test suite exercises settings bounds and opt-in defaults, delta-time
clamping, downward and sensor-directed vector gravity, display-axis and sprite
rotation mapping, shake filtering/strength/cooldown, amplified detonation,
gravity-relative hops, floor and sideways-wall settling, drag momentum,
collision separation, the 100-pet cap, kill commands, settings propagation,
dashboard state selection, conditional motion controls, 200 ms live-save
debouncing, popover placement at screen edges, two-tap danger confirmation,
and the fixed-header/scroll-container hierarchy. Version 0.5.0 adds coverage
for mixed-species coexistence, globally unique IDs, a one-Hoots cap,
species-scoped removal and chaos, independent settings/bounds/gravity,
cross-species collision gating, species-specific dashboard states, and sensor
routing when one or both pets enable tilt and shake.
The Hoots idle/physics update adds coverage for equal-choice book, writing, and
wing-movement idle activities; floating idle animation; force suppression;
dragging without throw momentum; and collision against an immovable Hoots.
Version 0.6.1 introduced package filtering, text sanitization and length limits,
newest-active selection, update/removal fallback, disabled/disconnected cache
clearing, and edge/keyboard-safe Hoots bubble placement coverage.
The appearance follow-up adds coverage for default/reset values, colour and
opacity normalization, passive versus interactive window flags, the live
hold-menu setting snapshot, and Hoots-only idle sprite normalization.
Version 0.7.0 adds regression coverage for independent border/fill/text
opacity bounds, typed-number parsing/clamping/step snapping, and Hoots's
species-level 100% size baseline.

The Hoots sheet is a 1536 x 2288 RGBA WebP arranged as the same 8 x 11,
192 x 208 cell grid as Seedy. Imported sheet SHA-256:

```text
62539958eb6ab2b23c6cb640c6ca9516a42a75e785203eee3eeff4d9c4581456
```

## Artifact

The root output directory contains `PetBlud-0.7.0-debug.apk`.

```text
Size: 11,864,742 bytes
SHA-256: 87797e2766b7d4bc675617d6060e55c8cfaf8fe7bc23910ec35d069de5dcf78a
```

## Remaining device checks

No Android device or emulator was attached during this build. In addition to
the original overlay acceptance checks, real sensor-axis behavior in every
display rotation, shake sensitivity, rotated-sprite touch handling, and
keyboard avoidance with several IMEs still need device testing. The new
service-owned popover additionally needs API 26, 31, and 34+ checks for
outside-touch pass-through, every display edge, rotation, screen-off dismissal,
TalkBack, large text, landscape, and keyboard visibility. Reliable keyboard
insets are implemented on API 30 and newer; API 26–29 retain best-effort full
display bounds. Version 0.5.0 additionally needs simultaneous Seedy/Hoots
overlay checks, repeated Hoots wake requests, cross-collision behavior,
independent motion/keyboard settings, pager swipes, and both species' menus.
The Hoots physics-interaction toggle still needs touch, blast, sensor-shake,
floating-idle, and cross-collision acceptance testing on a physical device.
Version 0.7.0 additionally needs notification-access grant/revoke checks, real
ChatGPT post/update/removal events, in-memory caching while Hoots is absent,
bubble following and touch pass-through, keyboard clamping, listener reconnect,
and confirmation that every non-ChatGPT package is ignored.
Bubble colour/independent opacity rendering, typed numeric input, reset behavior, hold-menu disabling,
tap-to-open, touch pass-through in both interaction modes, and visual sprite
scale consistency also remain physical-device acceptance checks.
