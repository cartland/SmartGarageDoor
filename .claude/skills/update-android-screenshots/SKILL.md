---
description: Update Android reference screenshots for image tracking (not pixel-perfect verification).
allowed-tools: Bash(*), Read, Glob, Grep
user-invocable: true
---

# Update Android Screenshots

Generate reference screenshots for tracking app appearance over time. These are for documentation and visual history — NOT pixel-perfect CI verification. Screenshots never block CI or PRs.

## Purpose

- Track how the app looks across releases
- Generate Play Store assets and documentation images
- Provide visual history of UI changes in git
- Light/dark theme variants for all key screens and components

## Steps

### 1. Generate screenshots sequentially (avoids OOM)

```bash
./scripts/generate-android-screenshots.sh
```

This runs each test file in its own Gradle invocation, then auto-generates the gallery.

### 2. Review the gallery

Open `AndroidGarage/android-screenshot-tests/SCREENSHOT_GALLERY.md` to see all screenshots.

### 3. Commit the references

```bash
git add AndroidGarage/android-screenshot-tests/src/screenshotTestDebug/reference/
git add AndroidGarage/android-screenshot-tests/SCREENSHOT_GALLERY.md
```

## Adding New Screenshots

1. Create or find a `@Preview` composable in the app code
2. Add a screenshot test in `android-screenshot-tests/src/screenshotTest/kotlin/.../`:

```kotlin
@PreviewTest
@Preview(showBackground = true, name = "Light")
@Preview(showBackground = true, name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MyComponentPreviewTest() {
    AppTheme {
        MyComponentPreview()
    }
}
```

3. Run `./scripts/generate-android-screenshots.sh` to generate the reference PNGs

## Notes

- `updateDebugScreenshotTest` and `validateDebugScreenshotTest` can't run in the same Gradle invocation
- To force single-invocation: `./gradlew :android-screenshot-tests:updateDebugScreenshotTest -PforceAllScreenshots`
- Preview composables must be deterministic — use fixed `Instant.parse(...)` for timestamps, never `Clock.System.now()`
- Reference PNGs are committed to git but are NOT validated in CI
- The gallery markdown is auto-generated — do not edit it manually
