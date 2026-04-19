# Running Instrumented Tests Under R8

Normal `connectedDebugAndroidTest` runs against an unminified debug build.
Some bugs only surface under release-level R8 minification (ADR-020, the
android/167 and android/170 snooze-state propagation regressions).
`SnoozeStateInstrumentedPropagationTest` passes in debug and fails on
device release APKs — debug instrumented runs cannot catch it.

This doc captures the minimum setup to run an instrumented test against
an R8-enabled APK using the existing `benchmark` build type.

## Prerequisites

- Connected device or emulator (`adb devices` non-empty)
- Uninstall any prior build of the benchmark variant
  (`adb uninstall com.chriscartland.garage.benchmark`) if install fails
  with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

## Run

```bash
AndroidGarage/gradlew -p AndroidGarage \
  -PtestR8=true \
  -PdebuggableBenchmark=true \
  :androidApp:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=\
com.chriscartland.garage.ui.SnoozeStateInstrumentedPropagationTest
```

### What the flags do

- `-PtestR8=true` — flips `testBuildType` from `debug` to `benchmark`, so
  `connectedAndroidTest` targets the R8-minified variant.
- `-PdebuggableBenchmark=true` — flips `benchmark.isDebuggable` to `true`.
  Instrumented tests cannot install on a non-debuggable APK. Leaving this
  off (the default) keeps the benchmark variant perf-accurate for actual
  benchmarks.
- `-Pandroid.testInstrumentationRunnerArguments.class=<FQN>` — filter to a
  single test class; omit to run the full instrumented suite under R8.
- If the device already has a conflicting version: add
  `-PVERSION_CODE=99999` to force install.

## Expected behavior

- Debug (default): `connectedDebugAndroidTest` runs the same test and it
  PASSES against the unminified chain.
- With `-PtestR8=true -PdebuggableBenchmark=true`: run against the
  minified APK. If R8 is the trigger for the snooze propagation
  regression, this run FAILS while the debug run PASSES — that's the
  reproduction.

## Why not just enable R8 in debug?

- Debug builds skip R8 for fast iteration — we don't want every
  `./gradlew assembleDebug` to run shrinker.
- The `benchmark` build type already initializes from `release` with the
  full proguard configuration, which is exactly what we want to reproduce
  production bugs.

## Troubleshooting

- **`No matching client found` from google-services**: the package name
  `com.chriscartland.garage.benchmark` is registered in
  `androidApp/google-services.json`. If you see this error, verify the
  file still contains `com.chriscartland.garage.benchmark`.
- **`Test framework quit unexpectedly`**: usually means the APK crashed
  at startup. `adb logcat | grep AndroidRuntime` to find the stack.
  Common causes: missing R8 keep rule for a newly-introduced serializable
  type (ADR-020).
- **Tests pass in R8 too**: if the symptom you're investigating
  reproduces on production devices but not under this harness, the
  trigger is probably NOT R8 — look at lifecycle / process-death / Play
  Store signing differences instead.
