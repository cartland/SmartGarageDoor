# R8 keep rules — what gets stripped, how to detect, how to prevent

**Who this is for:** Android developers shipping release builds with
R8/ProGuard minification enabled. Covers the categories of code that
R8 silently strips, the symptoms, and the minimum keep rules that
survive the next library upgrade.

## TL;DR

R8 removes code it believes is unused. It's wrong about any code
reached via reflection, annotation-processing generated code, JNI,
service loader, or generic type information. Symptoms in release
builds only. Debug instrumented tests will not catch them because
debug builds skip R8.

Write **keep rules** for: serialization infrastructure (both the
framework's and your own data classes), HTTP client request/response
types, classes looked up by name (`Class.forName`), and anything
reached through a deferred generic `T::class`.

## The failure mode

### Symptom categories

- **`ClassNotFoundException` / `NoSuchMethodException`** — obvious.
  The class was stripped; a reflection lookup failed at runtime.
- **`@Serializable` fields silently parse as null / default values** —
  much worse. The field name survives (because it's read by the
  generated serializer) but the `$serializer` or `$Companion` class
  got stripped. Runtime "succeeds" but the object is empty.
- **Ktor/Retrofit `body<T>()` returns wrong-shaped objects** — same
  mechanism. The request/response type's generated serializer got
  stripped.
- **`Class.forName("some.package.ClassName")` fails** — classes
  reached only via name-based lookup are invisible to R8.
- **`enumValueOf<T>()` fails** — generic enum lookup needs keep rules
  for each enum that's only referenced through the generic parameter.

### Why R8 doesn't know

R8 walks the call graph from your app's entry points (`onCreate`,
service classes declared in manifest, etc.) and keeps what's reachable.
When a class is only reached through:
- A string name (`Class.forName`, `Intent` action strings, R8 sees no edge)
- A generic type erased at runtime (`inline fun <reified T> decode()` —
  R8 sees erased type at the call site)
- Annotation-processor-generated code that R8 processes separately
  (kotlinx.serialization, kotlin-parcelize)
- A reflection lookup (`KClass.members`)

…R8 treats it as unreachable and strips it. The stripped code was
always going to run in production; R8 just didn't know.

### Why debug builds "work"

Debug builds disable R8 (`isMinifyEnabled = false`). The strip never
happens. Every instrumented test against a debug variant therefore
runs with the full class set intact. The bug only appears when R8 is
on — i.e., release APKs and Play Store internal/production tracks.

## Categories of code that need keep rules

### 1. kotlinx.serialization

The generated `$serializer` inner class for `@Serializable` data
classes is reflectively resolved. kotlinx.serialization ships some
consumer rules but real-world apps need to reinforce them:

```
-keepattributes *Annotation*, InnerClasses

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class **
-keep class <1>$$serializer { *; }

-if @kotlinx.serialization.Serializable class **
-keep class <1>$Companion { *; }

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
```

### 2. Ktor client request/response types

`response.body<T>()` relies on T's generated serializer at runtime.
Even with the serialization rules above, the specific data classes
your Ktor client uses should be kept explicitly:

```
-keep class com.example.network.** { *; }
-keepclassmembers class com.example.network.** { *; }
```

Narrow the package as much as you can; `com.example.**` is too
broad and defeats R8's shrinking elsewhere.

### 3. Classes looked up by string name

Anywhere you see `Class.forName(...)`, `ClassLoader.loadClass(...)`,
or an Intent whose component name is a string literal:

```
-keep class com.example.services.** { *; }
```

### 4. Parcelable / Parcelize

`@Parcelize` classes are kept by the plugin's consumer rules in
newer versions. Verify at the proguard rules output if you use an
older version.

### 5. Enums reached through generics

```kotlin
inline fun <reified T : Enum<T>> parseEnum(name: String): T =
    enumValueOf(name)
```

R8 doesn't see which enum gets substituted. Keep the enums:

```
-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
```

### 6. Your own reflection targets

If any part of your code uses `::class.memberProperties`,
`KClass.constructors`, or accesses methods by string name, keep the
target class and its members.

## How to detect stripped code before users do

### Detect via diagnostic logging at the deserialization boundary

When a `@Serializable` class returns unexpectedly empty / null fields,
the default reaction is "the server returned bad data." That's
usually wrong. The server was fine; R8 stripped the serializer.

**Log the raw body** at the Ktor/Retrofit response boundary, then
log the parsed type. When the raw body contains JSON with your
expected fields but the parsed object is empty, R8 is the culprit.

```kotlin
val text = response.bodyAsText()
Logger.d { "Raw response: $text" }
val parsed = Json.decodeFromString<MyResponse>(text)
Logger.d { "Parsed: $parsed" }
```

Leave the log behind for state-critical decode paths — Rule 9-style
observability. Adding it is cheap; removing it after a bug is
painful because the bug has already shipped.

### Detect via release-variant smoke tests

Build a `benchmark` variant that inherits from `release` (R8 on) but
is signed with a debug key and optionally debuggable. Install on a
device and run instrumented tests against it. If the test targets
`:app:connectedBenchmarkAndroidTest`, the APK under test is R8-
minified.

This catches the major ClassNotFoundException-class failures. It
won't catch "fields parse as null" unless your test asserts on
specific field values — which you should anyway.

### Detect via release-APK smoke pre-release

Before every release tag, install the release APK on a device
(internal testing track is ideal) and walk through critical flows.
R8 bugs tend to cluster at JSON boundaries and reflection sites —
make sure your smoke script touches both.

## Minimum viable defense (for any project adopting R8)

- [ ] Add the kotlinx.serialization keep rules above (if you use it).
- [ ] Narrowly keep your network-layer data packages (`Ktor`, Retrofit).
- [ ] Write a "raw body logged at decode" diagnostic at every
      state-critical deserialization boundary.
- [ ] Add a release-variant instrumented test harness that at least
      one critical-path test can run against.
- [ ] Smoke test the release APK on a physical device before every
      release tag, not just debug.

## Common pitfalls

### Pitfall: "consumer rules ship with the library, so I don't need my own"

Partially true. Library consumer rules handle the framework level
(e.g., keeping `KSerializer` itself). They often do NOT cover your
specific `@Serializable` data classes — those need the `@Serializable
→ keep $serializer` if-match pattern shown above.

### Pitfall: "I'll just `-keep class **`"

Defeats R8 entirely. The APK balloons and launch time suffers. Keep
rules should be narrow to specific packages.

### Pitfall: "tests pass so R8 is fine"

Debug tests don't run R8. Release-variant tests (`connectedBenchmark
AndroidTest` on a minified build) are the only instrumented signal.
See the [R8 instrumented test recipe](../R8_INSTRUMENTED_TESTS.md) in
this repo for an implementation — the same pattern applies elsewhere.

### Pitfall: assuming stack traces point at R8

A stripped class usually doesn't crash with a clear "R8 stripped me"
message. It manifests as downstream `NullPointerException`,
deserialization-default-value bugs, or feature silently not working.
When a release-only bug appears, add R8 to your hypothesis list
before jumping to lifecycle / process-death theories.

## Reference

- [R8 / ProGuard docs — keep rules](https://developer.android.com/build/shrink-code)
- [kotlinx.serialization Android R8 rules](https://github.com/Kotlin/kotlinx.serialization#android)
