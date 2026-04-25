---
category: reference
status: active
last_verified: 2026-04-24
---
# DI Singleton Requirements and Verification

Reference for the kotlin-inject `@Singleton` scoping bug discovered during
the android/170 snooze-state investigation (2026-04-19). Captures what
the DI graph must guarantee, the kotlin-inject-specific constraints that
make or break those guarantees, and the mechanical checks that prove
singletons are actually singletons.

## Background

android/170 shipped ADR-022's "VM exposes repo `StateFlow` by reference"
pattern. Users reported the snooze card title did not update on save.
Investigation found the kotlin-inject-generated `InjectAppComponent.kt`
was 15 lines of empty subclass — **no `@Singleton` provider was being
cached**. Every `provideSnoozeRepository()` call constructed a fresh
`NetworkSnoozeRepository` with its own `MutableStateFlow<SnoozeState>`.

Initial load worked because each repo's `init { externalScope.launch {
doFetchSnoozeStatus() } }` populated its own flow, and the VM happened
to observe one of them. POST failed because the write went through a
different UseCase with a different repo instance, and the VM was reading
a different `_snoozeState` entirely.

The hotfix (android/172) restored a VM-local mirror + direct write — the
PR #354 pattern — which bypasses the DI split. The underlying DI bug
persists and affects every `@Singleton` provider, but most of them are
masked by incidental singletons elsewhere (Firebase SDK, Room flow, Ktor
client).

## Requirements

**R1: Every `@Provides @Singleton` binding resolves to one instance per
component.**
Two `component.provideX()` calls MUST return the same reference. Two
transitive paths to `X` (e.g., `component.viewModel` → `UseCaseA(X)` and
`component.viewModel` → `UseCaseB(X)`) MUST inject the same `X`.

**R2: State-owning repositories are `@Singleton` or their stored state
must survive construction.**
If a repo holds a `MutableStateFlow<T>`, that flow lives on the repo
instance. Two instances = two flows = propagation gap. The repo must be
`@Singleton` to keep the flow shared. Same rule for `Manager` classes
that own a state (ADR-015).

**R3: Every `@Singleton` binding is transitively held by exactly one DI
path.**
If Provider A depends on `X` and Provider B depends on `X`, and A and B
are both live in the VM, they must receive the same `X`. Violation
surface: UseCase constructors that each take a fresh repo because the
`provideX()` chain isn't cached. See snooze above.

**R4: ViewModels may be per-nav-entry, but their shared state must route
through singletons.**
`rememberViewModelStoreNavEntryDecorator<Screen>()` creates one
`ViewModelStore` per nav entry. Home and Profile have distinct
`RemoteButtonViewModel` instances by design. Those two VMs must share
state via `@Singleton` repositories — not by holding parallel mirrors.

## Constraints (kotlin-inject-specific)

kotlin-inject generates its scoped caching through an override of
**abstract entry points**. Entry points are `abstract val x: T` (or
`abstract fun x(): T`) declared on the `@Component` class. The generator
emits an override for each, calling `_scoped.get("<key>") { provideX() }`
when the provider's binding is scoped.

**C1: Concrete `val x: T @Provides get() = …` does NOT generate a scoped
override.**
From the compiler's point of view, there is nothing to override — the
property already has an implementation. It sits on the `AppComponent`
class unchanged; every access runs the `get()` body and constructs a new
instance. The `@Singleton` annotation is silently ignored for this
provider, and transitively for any provider that depends on it without
its own abstract entry.

**C2: `@Component` classes that lack any abstract entry point generate
an empty subclass.**
The symptom: `build/generated/ksp/*/kotlin/.../InjectAppComponent.kt` is
only a few lines, with `override val _scoped: LazyMap = LazyMap()` and
no other overrides. Every `_scoped.get(…)` call site is absent because
nothing abstract forced one to exist.

**C3: The scope annotation must match the `@Component`'s scope.**
`AppComponent` is `@Singleton` (a `@Scope`-meta-annotated marker in
`di/Singleton.kt`). Providers on this component must be annotated with
the same `@Singleton` to be eligible for caching. A provider annotated
with a different scope, or none at all, is unscoped (fresh per call).

**C4: Constructor-injected `@Inject` classes respect their own
annotations.**
If a class is declared `@Inject class FooRepo @Singleton (...)`, the
scope is preserved when the component builds it. But our repositories
don't use `@Inject`; they're constructed via `@Provides` functions, so
C1-C3 apply.

## How to detect this bug

### Automated: `checkSingletonCaching`

`validate.sh` runs `./gradlew checkSingletonCaching`, a build-time check
that parses every `@Provides @Singleton fun provideX(...)` in
`AppComponent.kt` and verifies a matching `_scoped.get(...) { provideX() }`
call exists in the generated `InjectAppComponent.kt`. If any provider is
marked `@Singleton` but not cached, the task fails with a list of the
offenders and a pointer back to this doc. Runs on every local validation
and every PR — the android/170 regression shape cannot land silently.

The task source is `AndroidGarage/buildSrc/src/main/kotlin/architecture/SingletonCachingCheckTask.kt`.

### Mechanical: inspect the generated component

```bash
find AndroidGarage -path '*/ksp/*' -name 'InjectAppComponent.kt' | head -1
```

Read the file. Healthy generation for an app of this size produces
**hundreds of lines** — one override per abstract entry, each calling
`_scoped.get(...)` or computing the instance directly. If the file is
**under 20 lines** and has no `override val <provider>` lines, scoping
is broken. (Use this if `checkSingletonCaching` reported a failure and
you want to see the exact missing overrides.)

### Static: run a reference-identity test

Add to `androidApp/src/androidTest/java/.../di/ComponentGraphTest.kt`:

```kotlin
@Test
fun singletonRepositoriesReturnSameInstance() {
    val c = (ApplicationProvider.getApplicationContext<GarageApplication>()).component
    assertSame(c.provideSnoozeRepository(), c.provideSnoozeRepository())
    assertSame(c.provideAuthRepository(), c.provideAuthRepository())
    assertSame(c.provideDoorRepository(), c.provideDoorRepository())
    assertSame(c.provideServerConfigRepository(), c.provideServerConfigRepository())
    assertSame(c.provideDoorFcmRepository(), c.provideDoorFcmRepository())
    assertSame(c.provideFcmRegistrationManager(), c.provideFcmRegistrationManager())
}
```

With the current code this FAILS for every line — the strongest possible
proof of the bug. After the fix (abstract entry points), it passes. This
test is the load-bearing regression guard.

### Runtime: Rule 9 log count

Every `@Singleton` state-owning repo writes a Rule 9 log on init-fetch
(`snoozeState <- NotSnoozing (source=GET)` etc.). On a healthy cold
start, each such message appears **once per type**. Under the current
bug, a type with N injection paths will emit N init-fetch logs.

```bash
adb logcat -c
# Cold-launch the app
adb logcat -s com.chriscartland.garage.debug:I | grep -E "snoozeState <-|authState <-|serverConfig <-|currentDoorEvent <-"
```

Count the distinct `(source=…)` lines on startup. More than one of any
type = multiple instances. The count matches the number of times
`provideX()` appears transitively in the graph.

### Runtime: network fetch count

Each `@Singleton` repo's init-fetch hits the server once. With multiple
instances, the server sees N startup requests for the same endpoint.
Check the server-side access log on a cold start; if there are three
`/fetchSnoozeStatus` calls within 500ms of app launch, three repos
exist.

## How to fix (fix-direction, not prescriptive)

Convert `AppComponent` to use the abstract-entry-point shape:

```kotlin
@Component
@Singleton
abstract class AppComponent(@get:Provides val application: Application) {
    // Entry points — kotlin-inject generates scoped overrides for these.
    abstract val remoteButtonViewModel: DefaultRemoteButtonViewModel
    abstract val authViewModel: DefaultAuthViewModel
    abstract val doorViewModel: DefaultDoorViewModel
    // ...

    // Provider functions — same bodies as before, but declared here rather
    // than as concrete `val x: T @Provides get() = ...`.
    @Provides @Singleton
    protected fun provideSnoozeRepository(...): SnoozeRepository =
        NetworkSnoozeRepository(...)
    // ...
}
```

After regenerating, `InjectAppComponent.kt` contains one `override` per
abstract entry, each resolving its graph with scoped caching. The
identity test above passes. Every VM graph shares one repo per
`@Singleton` type.

## Verification checklist for the fix

- [ ] `InjectAppComponent.kt` (post-regen) has ≥ N `override` declarations
  (one per abstract entry). Count > 20 lines is a coarse sanity check.
- [ ] `ComponentGraphTest.singletonRepositoriesReturnSameInstance` passes.
- [ ] Rule 9 logcat shows **one** `source=GET` emission per state-owning
  type on cold start.
- [ ] Manual: on a running app, save a snooze. With the hotfix's direct-
  write removed (as a revert-to-ADR-022 experiment), the card title
  still updates (proving the repo's `_snoozeState` flow is shared with
  the VM's observation path).
- [ ] `AuthRepository` test: sign-in updates `authState` for every VM in
  every nav entry simultaneously — not just one.

## What to re-enable after the fix

- ADR-022 Rule 2 (VM exposes repo `StateFlow` by reference): revive
  after the identity test passes. Without DI-level singleton guarantees,
  the by-reference pattern has no shared state to reference.
- `ViewModelStateFlowCheckTask.bannedStateTypesInViewModels`: re-populate
  with `SnoozeState`, `AuthState`, `FcmRegistrationStatus` once Rule 2
  is restored.
- Remove the VM-local `_snoozeState` mirror + direct write in
  `RemoteButtonViewModel` (the android/172 hotfix) since the repo's
  single shared `StateFlow` will now propagate to the VM directly.

## Scope note

This doc covers `@Singleton` scoping in `AppComponent` for kotlin-inject
specifically. Other scope annotations, child components, and `@Inject`
classes (if added later) have their own rules. Consult the kotlin-inject
README before introducing a new scope.
