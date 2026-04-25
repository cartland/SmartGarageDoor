# kotlin-inject scoping — gotchas and verification

**Who this is for:** anyone using
[kotlin-inject](https://github.com/evant/kotlin-inject) on a project,
regardless of codebase. Covers the single class of bug that will break
`@Singleton` in production while passing every test you write by hand.

> **Status note for this repo:** the failure mode below was hit in
> production as the android/170 snooze regression. Fixed permanently
> in android/173–android/174 (2026-04-19) by converting all
> `@Singleton` providers to abstract entry points. The
> `checkSingletonCaching` Gradle task in `validate.sh` blocks
> regressions. Treat the symptoms below as a *historical case study*
> — current `AppComponent` is correct. See
> `archive/POSTMORTEM_ANDROID_170.md` for the timeline.

## TL;DR

kotlin-inject only generates **scoped caching** (what `@Singleton`
promises) when your `@Component` class has **abstract entry points**
that the code generator can override. If every provider is a concrete
`val x: T @Provides get() = ...` or concrete `fun provideX() = ...`,
the compiler has nothing to override and emits an empty subclass —
your `@Singleton` annotations become decorative. The generated
`InjectAppComponent.kt` is the only source of truth.

## The failure mode

### What looks correct in source

```kotlin
@Component
@Singleton
abstract class AppComponent(
    @get:Provides val application: Application,
) {
    val snoozeRepository: SnoozeRepository
        @Provides @Singleton get() = NetworkSnoozeRepository(...)

    @Provides @Singleton
    fun provideAuthRepository(): AuthRepository =
        FirebaseAuthRepository(provideAuthBridge(), provideLoggerRepo(), ...)
    // ...
}
```

`@Singleton` is present on every provider. `@Component @Singleton` is
present on the class. A reviewer sees nothing wrong.

### What the compiler actually emits

```kotlin
// build/generated/ksp/*/kotlin/.../InjectAppComponent.kt
public class InjectAppComponent(application: Application) :
    AppComponent(application),
    ScopedComponent {
  override val _scoped: LazyMap = LazyMap()
}
```

Fifteen lines. `_scoped` is initialized and never consulted. No
override of any provider. Every access to
`component.snoozeRepository` runs the concrete `get()` body, which
constructs a fresh `NetworkSnoozeRepository`. `@Singleton` is ignored.

### Why

kotlin-inject's code generator only overrides **abstract** members of
the `@Component` class. If a provider is concrete, there's nothing to
override — the Kotlin compiler resolves the call directly to the
hand-written body, which runs every time it's called.

Furthermore, scoped caching is only inserted when kotlin-inject
**routes** the binding. When a `@Provides fun` body calls a sibling
`@Provides fun` directly (e.g. `FirebaseAuthRepository(provideAuthBridge(), ...)`),
those calls are regular Kotlin — they bypass the `_scoped` cache.
Only parameters declared on the function signature get scope-routed.

### Symptoms you'll see in production

- Multiple instances of what should be a singleton repository.
- State-owning repositories (that hold `MutableStateFlow<T>`, a
  `Mutex`, or any cache) exhibiting per-consumer fragmentation:
  different ViewModels see different states for the same domain type.
- Writes to "the repository's flow" not reaching subscribers that read
  "the repository's flow" — because they're different flow objects.
- Debug tests pass because tests wire instances manually, bypassing the
  broken DI graph entirely.

## The two mechanical rules that make `@Singleton` work

### Rule 1: Abstract entry points for every `@Singleton` provider

kotlin-inject's generator inserts `_scoped.get("<key>") { provideX() }`
into **overrides of abstract members**. Make every scoped binding
reachable through an `abstract val` (or `abstract fun`) on the
`@Component`.

```kotlin
@Component @Singleton
abstract class AppComponent(...) {
    // Entry points — one per @Singleton binding you want cached.
    abstract val snoozeRepository: SnoozeRepository
    abstract val authRepository: AuthRepository
    abstract val appDatabase: AppDatabase
    // ...

    // Provider bodies — separate concrete functions.
    @Provides @Singleton
    fun provideSnoozeRepository(...): SnoozeRepository = ...

    @Provides @Singleton
    fun provideAuthRepository(...): AuthRepository = ...
}
```

### Rule 2: Parameters, not sibling function calls, in `@Provides` bodies

If a `@Provides` function depends on another `@Provides`-produced type,
declare it as a **parameter**. Never call a sibling `provideY()` inside
the body — that call is regular Kotlin and skips the cache.

```kotlin
// BAD: sibling call bypasses _scoped.
@Provides @Singleton
fun provideAuthRepository(): AuthRepository =
    FirebaseAuthRepository(provideAuthBridge(), provideLoggerRepo(), ...)

// GOOD: parameters route through _scoped.
@Provides @Singleton
fun provideAuthRepository(
    authBridge: AuthBridge,
    loggerRepo: AppLoggerRepository,
    applicationScope: CoroutineScope,
): AuthRepository =
    FirebaseAuthRepository(authBridge, loggerRepo, applicationScope)
```

When the generator sees parameters, it resolves each one through the
binding graph — including the scoped accessor when the dep is
`@Singleton`.

## How to verify (three independent signals)

### 1. Read the generated component

```bash
# Path may vary; look for any InjectAppComponent*.kt under build/generated.
./gradlew kspDebugKotlin
cat $(find . -path '*/ksp/*' -name 'InjectAppComponent*.kt' | head -1)
```

Healthy file:
- Many `override val X: T` declarations (one per abstract entry).
- Each scoped override contains `_scoped.get("<key>") { ... }`.
- VM / non-scoped entry overrides resolve deps by calling the scoped
  accessor for each parameter.

Broken file:
- Under ~20 lines.
- Only `override val _scoped: LazyMap = LazyMap()`.
- No `override` for your `@Singleton` types.

### 2. Runtime identity test

```kotlin
@Test
fun snoozeRepositoryIsSingleton() {
    val c = (application as MyApp).component
    assertSame(c.snoozeRepository, c.snoozeRepository)
}

@Test
fun authRepositoryIsSingleton() {
    val c = (application as MyApp).component
    assertSame(c.authRepository, c.authRepository)
}
```

One `assertSame` per `@Singleton` entry point. Without these, the
annotation has no runtime verification — it's indistinguishable from
a decorator.

The failing-then-passing pair is the strongest regression guard:
- Before the fix: `assertSame` fails because two calls produce two
  instances.
- After the fix: `assertSame` passes.
- Future regression (someone collapses an abstract entry back to
  concrete, or adds a `@Singleton` binding without an abstract entry):
  `assertSame` fails at CI.

### 3. Log-based smoke in staging

If a `@Singleton` state-owning repository has an `init`-side-effect
(network fetch, listener registration, log line), that effect runs
**once per instance**. Cold-start logcat for an app with N bugged
instances shows N startup fetches. Healthy app shows 1.

## Minimum viable defense (copy into any new project adopting kotlin-inject)

- [ ] `@Component` class is abstract and declares `abstract val` for
      every `@Singleton` binding that state depends on.
- [ ] Every `@Provides fun` takes its deps as parameters; no sibling
      `provideX()` calls inside bodies.
- [ ] A `ComponentGraphTest` with one `assertSame(c.x, c.x)` per
      `@Singleton` binding, running on CI (instrumented).
- [ ] A one-line docstring at the top of the `@Component` class
      stating the two rules — future edits see the intent at edit time.
- [ ] Before merging any change to the `@Component`: regenerate KSP
      and eyeball the `Inject*Component.kt` output.

Four of these are cheap (<30 minutes to add). The identity test is
the single highest-value check — it is the only mechanical signal
that *actually* verifies `@Singleton` works.

## Common patterns and anti-patterns

### Anti-pattern: concrete `val x: T @Provides get()`

```kotlin
val snoozeRepository: SnoozeRepository
    @Provides @Singleton get() = NetworkSnoozeRepository(...)  // NOT cached
```

Convenient-looking, broken. `@Singleton` is ignored because there's
no abstract entry to override. Use either `abstract val x: T` +
separate `@Provides fun provideX()`, OR just `@Provides fun provideX()`
with no `val` at all.

### Anti-pattern: calling sibling providers in bodies

```kotlin
@Provides @Singleton
fun provideFooRepository(): FooRepository =
    NetworkFooRepository(provideBar(), provideBaz())  // bypass
```

Even if `provideBar` and `provideBaz` are `@Singleton`, those calls
construct fresh instances because they're resolved by the Kotlin
compiler, not by kotlin-inject. Rewrite with parameters.

### Pattern: testing entry points

Add `abstract val` for any `@Singleton` you want to identity-test even
if it's only consumed internally. A well-maintained component has an
entry point for every state-owning type.

### Pattern: per-Scope entry points

Singleton isn't the only scope kotlin-inject supports. If you define
custom scopes (e.g., `@UserSession`), the same rules apply: abstract
entry points on the component with that scope, parameter-based
providers, identity tests at the scope boundary.

## When things go wrong

### Symptom: `assertSame` fails for one binding, passes for others

One provider is concrete, or its body calls a sibling directly. Find
it — the generated file will be missing the `_scoped.get` call for
that type.

### Symptom: generated file grew but one binding still isn't cached

Check the provider's parameter list. If it has no parameters but the
concrete type constructor requires them, the compiler may be
accepting an empty provider and not warning. Pass all deps as
parameters.

### Symptom: adding a new `@Singleton` silently doesn't cache

Did you add an abstract entry? Without it, kotlin-inject has no
reason to generate an override. Add `abstract val newRepository: NewRepository`
and rerun KSP.

## Reference

- [kotlin-inject README — Scopes](https://github.com/evant/kotlin-inject/blob/main/README.md#scopes)
- [kotlin-inject `@Component`](https://github.com/evant/kotlin-inject/blob/main/README.md#component)

## Provenance of this guide

This guide was written after a ~5-month regression cycle where
`@Singleton` silently didn't cache, producing a user-visible state
propagation bug that masqueraded as a Compose/Flow issue and survived
multiple targeted fix attempts. The repository-specific postmortem is
in `../archive/POSTMORTEM_ANDROID_170.md`; this guide contains only the
portable lessons.

If you're adopting kotlin-inject in a new project, the four-item
"Minimum viable defense" above is the most cost-effective protection
against repeating this mistake.
